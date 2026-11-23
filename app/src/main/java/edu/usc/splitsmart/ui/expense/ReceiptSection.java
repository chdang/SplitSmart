package edu.usc.splitsmart.ui.expense;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import edu.usc.splitsmart.AppServices;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.logic.Callback;
import edu.usc.splitsmart.model.Receipt;
import edu.usc.splitsmart.ui.common.ErrorMessages;

/**
 * The Receipts part of the expense form: a row of thumbnails, an "Attach receipt" button, and
 * a viewer that opens when a thumbnail is tapped.
 *
 * <p>It works in two modes. While the expense hasn't been saved yet (NewExpenseForm), picked
 * photos wait as files in the cache and are uploaded by {@link #uploadPending} once the expense
 * exists. After {@link #showSaved} (EditExpenseForm), adding or deleting a receipt is saved
 * straight away. Members who can't edit the expense can still view its receipts.
 */
final class ReceiptSection {

    private static final String STATE_PENDING = "receipt_pending";
    private static final int THUMBNAIL_EDGE = 256;

    /** Photo decoding and file reads happen here so the screen never stutters. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    /** A receipt already in the database, or a photo waiting for the expense to be saved. */
    private static final class Item {
        @Nullable final Receipt saved;
        @Nullable final File pending;
        @Nullable Bitmap thumbnail;

        Item(@Nullable Receipt saved, @Nullable File pending) {
            this.saved = saved;
            this.pending = pending;
        }
    }

    private final AppCompatActivity activity;
    private final Consumer<CharSequence> messages;
    private final LinearLayout container;
    private final View scroll;
    private final TextView empty;
    private final Button addButton;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker;
    private final List<Item> items = new ArrayList<>();

    /** Null until the expense exists; while null, new receipts are kept as pending files. */
    @Nullable private String expenseId;
    private String actorId;
    private boolean editable = true;
    private boolean busy;

    /** Must be created in onCreate, since it registers for the photo picker's result. */
    ReceiptSection(AppCompatActivity activity, Consumer<CharSequence> messages) {
        this.activity = activity;
        this.messages = messages;
        container = activity.findViewById(R.id.receipts_container);
        scroll = activity.findViewById(R.id.receipts_scroll);
        empty = activity.findViewById(R.id.receipts_empty);
        addButton = activity.findViewById(R.id.add_receipt_button);
        picker = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), this::onPicked);
        addButton.setOnClickListener(v -> picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        render();
    }

    // ---------------------------------------------------------------------------------------
    // Called by the form
    // ---------------------------------------------------------------------------------------

    /** Only the expense's creator may add or delete receipts; everyone else just views them. */
    void setEditable(boolean canEdit) {
        editable = canEdit;
        updateAddButton();
    }

    /** Stops new picks while the form is saving or deleting. */
    void setBusy(boolean isBusy) {
        busy = isBusy;
        updateAddButton();
    }

    /** Switches to saved mode and loads the expense's receipts. */
    void showSaved(String savedExpenseId, String currentUserId) {
        expenseId = savedExpenseId;
        actorId = currentUserId;
        AppServices.receipts().getReceipts(savedExpenseId, onUi(list -> {
            items.clear();
            for (Receipt receipt : list) {
                items.add(new Item(receipt, null));
            }
            render();
        }));
    }

    /**
     * Uploads the photos picked before the expense existed, then runs {@code done}. If the
     * upload fails the expense itself is still saved, so the user is told and the form closes.
     */
    void uploadPending(String newExpenseId, String currentUserId, Runnable done) {
        List<File> files = pendingFiles();
        if (files.isEmpty()) {
            done.run();
            return;
        }
        WORKER.execute(() -> {
            List<byte[]> images = new ArrayList<>();
            try {
                for (File file : files) {
                    images.add(Files.readAllBytes(file.toPath()));
                }
            } catch (IOException unreadable) {
                images = null;
            }
            List<byte[]> toSend = images;
            activity.runOnUiThread(() -> {
                if (toSend == null) {
                    failUpload(done);
                    return;
                }
                // Deliberately not onUi: the upload must finish even if the screen is closing.
                AppServices.receipts().addReceipts(newExpenseId, currentUserId, toSend,
                        new Callback<List<Receipt>>() {
                            @Override
                            public void onSuccess(List<Receipt> saved) {
                                discardPending();
                                done.run();
                            }

                            @Override
                            public void onError(Exception error) {
                                failUpload(done);
                            }
                        });
            });
        });
    }

    /** Deletes cached photos that were never uploaded. Call when the screen is finishing. */
    void discardPending() {
        for (File file : pendingFiles()) {
            file.delete();
        }
    }

    void saveState(Bundle out) {
        ArrayList<String> paths = new ArrayList<>();
        for (File file : pendingFiles()) {
            paths.add(file.getAbsolutePath());
        }
        out.putStringArrayList(STATE_PENDING, paths);
    }

    /** Brings back photos picked before a rotation. Saved receipts reload via showSaved. */
    void restoreState(@Nullable Bundle saved) {
        List<String> paths = saved == null ? null : saved.getStringArrayList(STATE_PENDING);
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            File file = new File(path);
            if (file.exists()) {
                items.add(new Item(null, file));
            }
        }
        render();
    }

    // ---------------------------------------------------------------------------------------
    // Adding
    // ---------------------------------------------------------------------------------------

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;   // the user backed out of the picker
        }
        setBusy(true);
        File pendingDir = new File(activity.getCacheDir(), "receipts_pending");
        WORKER.execute(() -> {
            byte[] jpeg;
            File file = null;
            try {
                jpeg = ReceiptImages.encode(activity.getContentResolver(), uri);
                if (expenseId == null) {
                    file = writePending(pendingDir, jpeg);
                }
            } catch (IOException | RuntimeException unreadable) {
                jpeg = null;
            }
            byte[] result = jpeg;
            File pendingFile = file;
            activity.runOnUiThread(() -> {
                if (isClosing()) {
                    return;
                }
                if (result == null) {
                    setBusy(false);
                    messages.accept(activity.getString(R.string.receipt_unreadable));
                } else if (pendingFile != null) {
                    setBusy(false);
                    items.add(new Item(null, pendingFile));
                    render();
                } else {
                    upload(result);
                }
            });
        });
    }

    private void upload(byte[] jpeg) {
        AppServices.receipts().addReceipts(expenseId, actorId, Collections.singletonList(jpeg),
                onUi(saved -> {
                    setBusy(false);
                    for (Receipt receipt : saved) {
                        items.add(new Item(receipt, null));
                    }
                    render();
                    messages.accept(activity.getString(R.string.receipt_attached));
                }));
    }

    private static File writePending(File dir, byte[] jpeg) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File file = new File(dir, UUID.randomUUID() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(jpeg);
        }
        return file;
    }

    private void failUpload(Runnable done) {
        Toast.makeText(activity, R.string.receipt_save_failed, Toast.LENGTH_LONG).show();
        discardPending();
        done.run();
    }

    // ---------------------------------------------------------------------------------------
    // Showing, viewing, deleting
    // ---------------------------------------------------------------------------------------

    private void render() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            ImageView thumb = (ImageView) inflater.inflate(R.layout.item_receipt, container, false);
            thumb.setContentDescription(activity.getString(R.string.receipt_thumbnail, i + 1));
            thumb.setOnClickListener(v -> openViewer(item));
            container.addView(thumb);
            if (item.thumbnail != null) {
                thumb.setImageBitmap(item.thumbnail);
            } else {
                loadImage(item, bytes -> {
                    item.thumbnail = ReceiptImages.decode(bytes, THUMBNAIL_EDGE);
                    thumb.setImageBitmap(item.thumbnail);
                });
            }
        }
        boolean none = items.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        scroll.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    private void openViewer(Item item) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_receipt, null);
        ImageView image = content.findViewById(R.id.receipt_image);
        View loading = content.findViewById(R.id.receipt_loading);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.receipt_view_title)
                .setView(content)
                .setPositiveButton(R.string.receipt_close, null);
        if (editable) {
            builder.setNegativeButton(R.string.receipt_delete, (d, w) -> confirmDelete(item));
        }
        AlertDialog dialog = builder.show();
        loadImage(item, bytes -> {
            if (dialog.isShowing()) {
                loading.setVisibility(View.GONE);
                image.setImageBitmap(ReceiptImages.decode(bytes, ReceiptImages.MAX_EDGE));
            }
        });
    }

    private void confirmDelete(Item item) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.receipt_delete_title)
                .setMessage(R.string.receipt_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> delete(item))
                .show();
    }

    private void delete(Item item) {
        if (item.saved == null) {
            if (item.pending != null) {
                    item.pending.delete();
            }
            items.remove(item);
            render();
            return;
        }
        setBusy(true);
        AppServices.receipts().deleteReceipt(item.saved.getId(), actorId, onUi(done -> {
            setBusy(false);
            items.remove(item);
            render();
            messages.accept(activity.getString(R.string.receipt_deleted));
        }));
    }

    /** Gets the item's JPEG bytes from the database or the pending file. */
    private void loadImage(Item item, Consumer<byte[]> onLoaded) {
        if (item.saved != null) {
            AppServices.receipts().getReceiptImage(item.saved.getId(), onUi(onLoaded));
            return;
        }
        File file = item.pending;
        WORKER.execute(() -> {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file.toPath());
            } catch (IOException | RuntimeException missing) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isClosing()) {
                    onLoaded.accept(bytes);
                }
            });
        });
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private List<File> pendingFiles() {
        List<File> files = new ArrayList<>();
        for (Item item : items) {
            if (item.pending != null) {
                files.add(item.pending);
            }
        }
        return files;
    }

    private void updateAddButton() {
        addButton.setVisibility(editable ? View.VISIBLE : View.GONE);
        addButton.setEnabled(!busy);
    }

    private boolean isClosing() {
        return activity.isFinishing() || activity.isDestroyed();
    }

    /** Drops results that arrive after the screen closed; shows friendly text for errors. */
    private <T> Callback<T> onUi(Consumer<T> onSuccess) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                if (!isClosing()) {
                    onSuccess.accept(result);
                }
            }

            @Override
            public void onError(Exception error) {
                if (!isClosing()) {
                    setBusy(false);
                    messages.accept(ErrorMessages.from(activity, error));
                }
            }
        };
    }
}
