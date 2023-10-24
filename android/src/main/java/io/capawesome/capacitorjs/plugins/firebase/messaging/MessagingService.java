package io.capawesome.capacitorjs.plugins.firebase.messaging;

import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import android.app.NotificationChannel;
import android.content.Intent;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.core.app.Person;
import android.app.PendingIntent;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import android.content.pm.PackageManager;
import android.content.Context;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.IOException;
import android.app.Notification;
import android.app.NotificationManager;
import android.service.notification.StatusBarNotification;
import com.getcapacitor.Logger;

import androidx.core.content.ContextCompat;
import org.json.JSONException;
import org.json.JSONObject;
public class MessagingService extends FirebaseMessagingService {
    public static final String TAG = "FirebaseMessaging";
    private final String defaultNotificationChannel = "default";
    private final int defaultNotificationColor = 0;
    private NotificationManager notificationManager;
    private Context ctx;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseMessagingPlugin.onNewToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        ctx = this;

        notificationManager = ContextCompat.getSystemService(ctx, NotificationManager.class);

        // On Android O or greater we need to create a new notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          NotificationChannel defaultChannel = notificationManager.getNotificationChannel(defaultNotificationChannel);
          if (defaultChannel == null) {
            NotificationChannel mChannel = new NotificationChannel(defaultNotificationChannel, "Firebase",
              NotificationManager.IMPORTANCE_HIGH);
            mChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(mChannel);
          } else {
            defaultChannel.setShowBadge(true);
          }
        }

        try {
          JSONObject data = new JSONObject(remoteMessage.getData());
          Logger.info("remoteMessage" + data);
          boolean hasTag = !data.isNull("chatType") && !data.isNull("chatId") && !data.isNull("channelId");
          String tag = hasTag ? data.getString("chatType") + data.getString("chatId") + data.getString("channelId")
            : "";
          String eventType = data.getString("eventType");
          boolean isPaused = FirebaseMessagingPlugin.isPaused();
          if (eventType.equals("inputMessage") && hasTag && isPaused) {
            Runnable runnable = new Runnable() {
              public void run() {
                try {
                  boolean isDev = data.getBoolean("isDev");
                  boolean makePush = data.getBoolean("makePush");
                  JSONObject dataInData = new JSONObject(data.getString("data"));
                  JSONObject message = new JSONObject(dataInData.getString("message"));
                  String messageType = message.getString("type");
                  String title = dataInData.getString("contactName") + " — "
                    + dataInData.getString("channelName");
                  String text = messageType.equals("2") ? message.getString("filename")
                    : message.getString("text");
                  String avatar = dataInData.getString("avatar");
                  int chatUnanswered = dataInData.getInt("chatUnanswered");
                  Bitmap icon = getBitmapFromURL(
                    "https://store." + (isDev ? "dev-" : "") + "wazzup24.com/" + avatar);

                  if (messageType.equals("10")) {
                    title = dataInData.getString("contactName");
                    text = dataInData.getString("previewText");
                  }

                  Person person = new Person.Builder()
                    .setName(title)
                    .setIcon(IconCompat.createWithBitmap(icon))
                    .setKey(tag)
                    .build();

                  PackageManager pm = ctx.getPackageManager();
                  Intent startIntent = pm.getLaunchIntentForPackage(ctx.getPackageName());

                  startIntent.setAction(Intent.ACTION_MAIN);
                  startIntent.setPackage(ctx.getPackageName());
                  startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                  startIntent.putExtra("url", data.getString("chatType") + "/" + data.getString("chatId")
                    + "/" + data.getString("channelId"));
                  PendingIntent pendingIntent = PendingIntent.getActivity(ctx,
                    (int) System.currentTimeMillis(), startIntent, PendingIntent.FLAG_MUTABLE);

                  ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(ctx, tag)
                    .setIcon(IconCompat.createWithBitmap(icon))
                    .setIsConversation()
                    .setLongLabel(title)
                    .setLongLived(true)
                    .setPerson(person)
                    .setShortLabel(title)
                    .setIntent(startIntent)
                    .build();

                  ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut);

                  NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "default")
                    .setGroup(tag)
                    .setSmallIcon(
                      ctx.getResources().getIdentifier("icon", "drawable", ctx.getPackageName()))
                    .setColor(defaultNotificationColor)
                    .setAutoCancel(true)
                    .setNumber(chatUnanswered)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setContentIntent(pendingIntent)
                    .setLargeIcon(icon)
                    .setStyle(new NotificationCompat.MessagingStyle(person)
                      .addMessage(text, System.currentTimeMillis(), person))
                    .setShortcutId(tag)
                    .setPriority(NotificationCompat.PRIORITY_MAX);

                  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    StatusBarNotification[] nots = notificationManager.getActiveNotifications();
                    boolean hasNot = false;
                    for (int i = 0; i < nots.length; i++) {
                      String text2 = nots[i].getNotification().extras.getString("android.text");
                      String notTag = nots[i].getTag();
                      if (notTag.equals(tag) && text2.equals(text)) {
                        hasNot = true;
                      }
                    }
                    if (!hasNot && makePush)
                      notificationManager.notify(tag, 0, builder.build());
                  }
                } catch (JSONException e) {
                  Logger.error(TAG, "onMessageReceived JSONException", e);
                } catch (Exception e1) {
                  Logger.error(TAG, "onMessageReceived Exception", e1);
                }
              }
            };
            Thread thread = new Thread(runnable);
            thread.start();
          }
          if (eventType.equals("outputMessage") || eventType.equals("clearUnanswered")) {
            notificationManager.cancel(tag, 0);
          }
        } catch (JSONException e) {
          Logger.error(TAG, "onMessageReceived JSONException", e);
        } catch (Exception e1) {
          Logger.error(TAG, "onMessageReceived Exception", e1);
        }

        FirebaseMessagingPlugin.onMessageReceived(remoteMessage);
    }

    private Bitmap getBitmapFromURL(String src) {
      try {
        URL url = new URL(src);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.connect();
        InputStream input = connection.getInputStream();
        return BitmapFactory.decodeStream(input);
      } catch (IOException e) {
        // Log exception
        return null;
      }
    }
}
