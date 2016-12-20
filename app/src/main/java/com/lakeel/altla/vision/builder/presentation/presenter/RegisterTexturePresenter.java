package com.lakeel.altla.vision.builder.presentation.presenter;

import com.lakeel.altla.android.log.Log;
import com.lakeel.altla.android.log.LogFactory;
import com.lakeel.altla.vision.builder.R;
import com.lakeel.altla.vision.builder.presentation.model.EditTextureModel;
import com.lakeel.altla.vision.builder.presentation.view.RegisterTextureView;
import com.lakeel.altla.vision.domain.usecase.FindDocumentBitmapUseCase;
import com.lakeel.altla.vision.domain.usecase.FindDocumentFilenameUseCase;
import com.lakeel.altla.vision.domain.usecase.FindUserTextureBitmapUseCase;
import com.lakeel.altla.vision.domain.usecase.FindUserTextureUseCase;
import com.lakeel.altla.vision.domain.usecase.SaveUserTextureUseCase;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.inject.Inject;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public final class RegisterTexturePresenter {

    private static final Log LOG = LogFactory.getLog(RegisterTexturePresenter.class);

    @Inject
    FindUserTextureUseCase findUserTextureUseCase;

    @Inject
    FindUserTextureBitmapUseCase findUserTextureBitmapUseCase;

    @Inject
    FindDocumentBitmapUseCase findDocumentBitmapUseCase;

    @Inject
    FindDocumentFilenameUseCase findDocumentFilenameUseCase;

    @Inject
    SaveUserTextureUseCase saveUserTextureUseCase;

    private final CompositeSubscription compositeSubscription = new CompositeSubscription();

    private RegisterTextureView view;

    private long prevBytesTransferred;

    private final EditTextureModel model = new EditTextureModel();

    private boolean localTextureSelected;

    @Inject
    public RegisterTexturePresenter() {
    }

    public void onCreate(@Nullable String id) {
        LOG.d("onCreate(): id = %s", id);

        model.textureId = id;
    }

    public void onCreateView(@NonNull RegisterTextureView view) {
        this.view = view;

        view.setTextureVisible(false);
        view.setLoadTextureProgressVisible(false);
    }

    public void onStart() {
        LOG.d("onStart()");

        view.showModel(model);

        if (localTextureSelected) {
            LOG.d("onStart(): Loading the selected local bitmap.");

            localTextureSelected = false;

            if (model.name == null || model.name.length() == 0) {
                loadLocalTextureBitmapAndName();
            } else {
                loadLocalTextureBitmap();
            }
        } else if (model.textureId != null) {
            LOG.d("onStart(): Loading the existing texture.");

            // Load the texture information.
            LOG.d("Loading the texture: id = %s", model.textureId);

            Subscription subscription = findUserTextureUseCase
                    // Find the texture entry to get its name.
                    .execute(model.textureId)
                    .toSingle()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(entry -> {
                        LOG.d("Loaded the texture.");

                        model.name = entry.name;
                        view.showModel(model);

                        loadCachedTextureBitmap(entry.textureId);
                    }, e -> {
                        // TODO: How to recover.
                        LOG.w(String.format("Failed to load the texture: id = %s", model.textureId), e);
                    });
            compositeSubscription.add(subscription);
        } else {
            LOG.d("onStart(): Just onStart().");

            view.setTextureVisible(true);
        }
    }

    public void onStop() {
        compositeSubscription.clear();
    }

    public void onClickButtonSelectDocument() {
        view.showLocalTexturePicker();
    }

    public void onLocalTextureSelected(@NonNull Uri uri) {
        localTextureSelected = true;

        model.localUri = uri;

        // Release the bitmap if needed.
        if (model.bitmap != null) {
            model.bitmap.recycle();
            model.bitmap = null;
        }
    }

    private void loadCachedTextureBitmap(String textureId) {
        view.setTextureVisible(false);

        Subscription subscription = findUserTextureBitmapUseCase
                .execute(textureId, null)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> view.setLoadTextureProgressVisible(true))
                .doOnUnsubscribe(() -> view.setLoadTextureProgressVisible(false))
                .subscribe(bitmap -> {
                    model.bitmap = bitmap;

                    view.setTextureVisible(true);
                    view.showModel(model);
                }, e -> {
                    // TODO: How to recover.
                    LOG.w(String.format("Failed to load the user texture bitmap: textureId = %s", textureId), e);
                });

        compositeSubscription.add(subscription);
    }

    private void loadLocalTextureBitmap() {
        view.setTextureVisible(false);

        Subscription subscription = findDocumentBitmapUseCase
                .execute(model.localUri)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> view.setLoadTextureProgressVisible(true))
                .doOnUnsubscribe(() -> view.setLoadTextureProgressVisible(false))
                .subscribe(bitmap -> {
                    model.bitmap = bitmap;

                    view.setTextureVisible(true);
                    view.showModel(model);
                }, e -> {
                    if (e instanceof FileNotFoundException) {
                        LOG.w(String.format("The image could not be found: uri = %s", model.localUri), e);
                        view.showSnackbar(R.string.snackbar_image_file_not_found);
                    } else if (e instanceof IOException) {
                        LOG.w("Closing file failed.", e);
                    } else {
                        LOG.e("Unexpected error occured.", e);
                        view.showSnackbar(R.string.snackbar_unexpected_error_occured);
                    }
                });
        compositeSubscription.add(subscription);
    }

    private void loadLocalTextureBitmapAndName() {
        view.setTextureVisible(false);

        Subscription subscription = Single
                .just(new LocalBitmap(model.localUri))
                // Load the bitmap.
                .flatMap(this::loadLocalBitmap)
                // Load the filename as a texture name if needed.
                .flatMap(this::loadLocalFilename)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> view.setLoadTextureProgressVisible(true))
                .doOnUnsubscribe(() -> view.setLoadTextureProgressVisible(false))
                .subscribe(localBitmap -> {
                    model.bitmap = localBitmap.bitmap;
                    model.name = localBitmap.name;

                    view.setTextureVisible(true);
                    view.showModel(model);
                }, e -> {
                    if (e instanceof FileNotFoundException) {
                        LOG.w(String.format("The image could not be found: uri = %s", model.localUri), e);
                        view.showSnackbar(R.string.snackbar_image_file_not_found);
                    } else if (e instanceof IOException) {
                        LOG.w("Closing file failed.", e);
                    } else {
                        LOG.e("Unexpected error occured.", e);
                        view.showSnackbar(R.string.snackbar_unexpected_error_occured);
                    }
                });
        compositeSubscription.add(subscription);
    }

    private Single<LocalBitmap> loadLocalBitmap(LocalBitmap localBitmap) {
        return findDocumentBitmapUseCase.execute(localBitmap.uri)
                                        .map(bitmap -> {
                                            localBitmap.bitmap = bitmap;
                                            return localBitmap;
                                        });
    }

    private Single<LocalBitmap> loadLocalFilename(LocalBitmap localBitmap) {
        return findDocumentFilenameUseCase.execute(localBitmap.uri)
                                          .map(filename -> {
                                              localBitmap.name = filename;
                                              return localBitmap;
                                          });
    }

    public void onClickButtonRegister() {

        String localUri = (model.localUri != null) ? model.localUri.toString() : null;

        Subscription subscription = saveUserTextureUseCase
                .execute(model.textureId, model.name, localUri, (totalBytes, bytesTransferred) -> {
                    long increment = bytesTransferred - prevBytesTransferred;
                    prevBytesTransferred = bytesTransferred;

                    view.setUploadProgressDialogProgress(totalBytes, increment);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> view.showUploadProgressDialog())
                .doOnUnsubscribe(() -> view.hideUploadProgressDialog())
                .subscribe(textureId -> {
                    // Update ID of the model.
                    model.textureId = textureId;

                    view.showSnackbar(R.string.snackbar_done);
                }, e -> {
                    LOG.e("Failed to save the user texture.", e);
                    view.showSnackbar(R.string.snackbar_failed);
                });
        compositeSubscription.add(subscription);
    }

    public void afterNameChanged(String filename) {
        model.name = filename;
    }

    private final class LocalBitmap {

        private final Uri uri;

        private Bitmap bitmap;

        private String name;

        LocalBitmap(Uri uri) {
            this.uri = uri;
        }
    }
}
