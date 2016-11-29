package com.lakeel.altla.vision.builder.presentation.view;

import com.lakeel.altla.vision.builder.presentation.model.EditTextureModel;

import android.support.annotation.StringRes;

public interface RegisterTextureView {

    void showLocalTexturePicker();

    void setTextureVisible(boolean visible);

    void setLoadTextureProgressVisible(boolean visible);

    void showModel(EditTextureModel model);

    void showUploadProgressDialog();

    void setUploadProgressDialogProgress(long max, long diff);

    void hideUploadProgressDialog();

    void showSnackbar(@StringRes int resId);
}
