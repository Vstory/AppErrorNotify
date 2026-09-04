
package io.github.vstory.apperrors.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import io.github.vstory.apperrors.ui.activity.errors.AppErrorsRecordActivity;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;


public class QuickStartTileService extends TileService {

    @SuppressWarnings("deprecation")
    @Override
    public void onClick() {
        super.onClick();
        try {
            Intent intent = new Intent(this, AppErrorsRecordActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
            else
                startActivityAndCollapse(intent);
        } catch (Exception e) {
            FunctionFactoryKt.toast(this, "Start " + AppErrorsRecordActivity.class.getName() + " failed");
        }
    }
}
