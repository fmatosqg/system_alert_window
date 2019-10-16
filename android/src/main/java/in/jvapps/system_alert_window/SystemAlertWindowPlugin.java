package in.jvapps.system_alert_window;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import in.jvapps.system_alert_window.services.WindowService;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static in.jvapps.system_alert_window.utils.Constants.INTENT_EXTRA_PARAMS_MAP;
import static in.jvapps.system_alert_window.utils.Constants.KEY_HEIGHT;

public class SystemAlertWindowPlugin extends Activity implements MethodCallHandler {

    Activity context;
    static MethodChannel methodChannel;
    public static int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1237;
    private static final String CHANNEL_ID = "1237";

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "system_alert_window");
        channel.setMethodCallHandler(new SystemAlertWindowPlugin(registrar.activity(), channel));
    }

    private SystemAlertWindowPlugin(Activity activity, MethodChannel newMethodChannel) {
        this.context = activity;
        methodChannel = newMethodChannel;
        methodChannel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "checkPermissions":
                checkPermission();
                result.success("checking permissions");
                break;
            case "showSystemWindow":
                WindowService.closeOverlayService();
                assert (call.arguments != null);
                @SuppressWarnings("unchecked")
                HashMap<String, Object> params = (HashMap<String, Object>) call.arguments;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    final Intent i = new Intent(context, WindowService.class);
                    context.stopService(i);
                    i.putExtra(INTENT_EXTRA_PARAMS_MAP, params);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            WindowService.enqueueWork(context, i);
                        }
                    }, 500);
                } else {
                    createBubble(params);
                }
                result.success(true);
                break;
            default:
                result.notImplemented();
        }
    }

    public static void invokeCallBack(String type, Object params) {
        List<Object> argumentsList = new ArrayList<>();
        argumentsList.add(type);
        argumentsList.add(params);
        methodChannel.invokeMethod("callBack", argumentsList);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void createBubble(HashMap<String, Object> params) {
        createNotificationChannel();
        Intent target = new Intent(context, BubbleActivity.class);
        target.putExtra(INTENT_EXTRA_PARAMS_MAP, params);
        PendingIntent bubbleIntent =
                PendingIntent.getActivity(context, 0, target, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create bubble metadata
        Notification.BubbleMetadata bubbleData =
                new Notification.BubbleMetadata.Builder()
                        .setDesiredHeight((int) params.get(KEY_HEIGHT))
                        .setIcon(Icon.createWithResource(context, R.drawable.ic_notification))
                        .setIntent(bubbleIntent)
                        .setAutoExpandBubble(true)
                        .setSuppressNotification(true)
                        .build();

        Person chatBot = new Person.Builder()
                .setBot(true)
                .setName("BubbleBot")
                .setImportant(true)
                .build();

        Notification.Builder builder =
                new Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setBubbleMetadata(bubbleData)
                        .addPerson(chatBot);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        int BUBBLE_NOTIFICATION_ID = 1237;
        notificationManager.notify(BUBBLE_NOTIFICATION_ID, builder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void createNotificationChannel() {
        CharSequence name = context.getString(R.string.channel_name);
        String description = context.getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        channel.setAllowBubbles(true);
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);
    }


    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "System Alert Window will not work without Can Draw Over Other Apps permission", Toast.LENGTH_LONG).show();
            }
        }

    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                context.startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
            }
        }
    }
}