package com.academmedia.notifylib;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.StringTokenizer;

/** _____ ADD IN APP MANIFEST _____ <service android:name="com.academmedia.notifylib.NotifyService"> </service> **/

/**
 * идея такая: из юнити приходит строка, которая содержит джейсон объекты, сохраняем её в преференсах апы
 * и устанавливаем алармы: сначала парсим строки, получаем уведомления, засовываем их в интент для сервиса
 * сервис получает интент и пушит уведомление, потом берёт айдишник из преференсов и инкрементит его,
 * если это было последние уведомление, и в карте стоит флаг "повторять уведомления" - снова вызваем метод
 * установки алармов. использовать преференсы пришлось из-за того что в сервисе свой класслоадер и статик
 * переменные в классе NotificationLib не сохраняются/извлекаются, т.к. загружается другой объект этого класса.
 * для отмены уведомлений берём массив айдишников, который записался в преференсы при парсинге карты уведомлений,
 * создаём интенты с такими же айдишниками и отменям соответствующие алярмы.
 */
public class NotificationsLib {

    final static String ICON = "app_icon"; // for android = ic_launcher for Unity = app_icon
    final static String NEXT_ID = "NEXT_ID";
    final static String ICON_ID = "ICON_ID";  //
    final static String TITLE = "TITLE";
    final static String LAUNCH_ACTIVITY = "LAUNCH_ACTIVITY";
    final static String NOTIFICATIONS_MAP = "NOTIFICATIONS_MAP";
    final static String IS_CYCLIC = "IS_CYCLIC";
    final static String NIDS = "NIDS";
    final static String NIDS_SIZE = "NIDS_SIZE";
    final static String BUNDLE_NOTIFICATION = "BUNDLE_NOTIFICATION";
    final static String NOTIFICATION_IN_BUNDLE = "NOTIFICATION_IN_BUNDLE";

    /******************************************************************
     * Set the map with notifications in application's shared preference
     ******************************************************************/
    public static void setNotificationMap(Activity activity, String jSonStrMap) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);

        try {
            // Validating Json String
            JSONObject jObject = new JSONObject(jSonStrMap);

            preferences.edit().putBoolean(IS_CYCLIC, jObject.getBoolean("interval")).apply();

            preferences.edit().putString(NOTIFICATIONS_MAP, jSonStrMap).apply();
            preferences.edit().putInt(NEXT_ID, 0).apply(); // switch to first notification after set new map

            // data for serviceIntent
            preferences.edit().putInt(ICON_ID, activity.getResources().getIdentifier(ICON, "drawable", activity.getPackageName())).apply();
            preferences.edit().putString(TITLE, activity.getTitle().toString()).apply();
            preferences.edit().putString(LAUNCH_ACTIVITY, activity.getClass().getName()).apply();

            Log.d("NotifyLib", "Notification Map has been set");

            // set alarms for first row of notifications
            setAlarms(activity);

        } catch (JSONException e) {
            //  if after successful map reset has been failed we will stop notifications
            preferences.edit().putInt(NEXT_ID, -1).apply();
            Log.e("NotifyLib", "JSon String is not valid. " + e.toString());
        }

    }

    /*****************************************************************************
    * after we have JsonStringMap with notifications this method should be called
     * for set alarms with each notification in map, or to set it again, after
     * the last notification has showed and we need to repeat notifications again
    *******************************************************************************/
    static void setAlarms(Context context) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.getInt(NEXT_ID, -1) == -1) {
            Log.e("NotifyLib", "Incorrect call setAlarms(): NEXT_ID == -1");
            return; //throw new RuntimeException();
        }

        //////////////////////////////////////////////////////////////////////
        // Building intent
        Intent intentForService = new Intent(context, NotifyService.class);
        intentForService.putExtra(ICON_ID, preferences.getInt(ICON_ID, 0));
        intentForService.putExtra(TITLE, preferences.getString(TITLE, ""));
        intentForService.putExtra(LAUNCH_ACTIVITY, preferences.getString(LAUNCH_ACTIVITY, ""));
        //
        //////////////////////////////////////////////////////////////////////

        //////////////////////////////////////////////////////////////////////
        // Get notifications from shared preferences
        String jStrMap = preferences.getString(NOTIFICATIONS_MAP, "");
        if (jStrMap != "") {

            Notification[] notifications = parseNotificationMap(jStrMap, context);

            // Set Alarms
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            long currentDelay = 0;
            long savedTime = System.currentTimeMillis();

            for (Notification notification : notifications) {

                if (notification.delay < 0) {
                    Log.e("NotifyLib", "Delay must be positive value.");
                    return;
                } //throw new RuntimeException();

                // put current notification into intent for service, we will read it in service
                // we need bundle because other way alarm manager will try to marshalling our notification object
                Bundle innerBundle = new Bundle();
                innerBundle.putParcelable(NOTIFICATION_IN_BUNDLE, notification);
                intentForService.putExtra(BUNDLE_NOTIFICATION, innerBundle);

                PendingIntent servicePendingIntent = PendingIntent.getService(context, notification.nid,
                        intentForService, PendingIntent.FLAG_UPDATE_CURRENT);  // FLAG_UPDATE_CURRENT

                currentDelay += notification.delay;
                alarmManager.set(AlarmManager.RTC, savedTime + currentDelay, servicePendingIntent);

            }

        } else {
            Log.e("NotifyLib", "JSonString is not found or Empty.");
            return;
        }
        ///////////////////////////////////////////////////////////////////////

    }

    /**********************************************************
    * This method will cancel all currently sets notifications
    **********************************************************/
    public static void cancelNotify(Activity activity) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        String nidsString = sharedPreferences.getString(NIDS, "");
        int nidsSize = sharedPreferences.getInt(NIDS_SIZE, -1);
        int nextId = sharedPreferences.getInt(NEXT_ID, -1);

        sharedPreferences.edit().putInt(NEXT_ID, -1).apply();
        sharedPreferences.edit().putString(NOTIFICATIONS_MAP, "");
        sharedPreferences.edit().putString(NIDS, "");

        if (nidsSize != -1 && nextId != -1) {

            // Create equals pending intent for cancel it same intent with any data
            Intent intentForNotify = new Intent(activity, NotifyService.class);

            StringTokenizer st = new StringTokenizer(nidsString, ",");
            int[] nids = new int[nidsSize];
            for (int i = 0; i < nidsSize; i++) {
                nids[i] = Integer.parseInt(st.nextToken());
            }

            for (int id : nids) {
                PendingIntent serviceIntent = PendingIntent.getService(activity, id, intentForNotify, 0);
                AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(serviceIntent);
            }

            Log.d("NotifyLib", "Notifications has been canceled");

        } else
            Log.w("NotifyLib", "Notifications already disposed");
    }


    /****************************************************
     *  returns id fot next notification which
     *  will appear in status bar
     ****************************************************/
    public static int getNextId(Activity activity) {

        // SharedPreferences sharedPreferences = activity.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        return sharedPreferences.getInt(NEXT_ID, -1);

    }


    /************************************************
     * returns array of Notification, that will
     * be created by parsing input String
     *************************************************/
    private static Notification[] parseNotificationMap(String jSonStrMap, Context context) {

        try {
            JSONObject jObject = new JSONObject(jSonStrMap);

            // array of notifications
            JSONArray jArrayNotifications = jObject.getJSONArray("notifications");

            Notification[] notifications = new Notification[jArrayNotifications.length()];

            // запилить nids в отдельный метод ? ><
            int[] nids = new int[jArrayNotifications.length()];
            /** === NOTIFICATIONS PARSING  */
            for (int i = 0; i < jArrayNotifications.length(); i++) {

                JSONObject jObjNotification = jArrayNotifications.getJSONObject(i);
                JSONArray jArrayMessages = jObjNotification.getJSONArray("messages");
                Message[] tmpNotifMessages = new Message[jArrayMessages.length()];

                /** === MESSAGES PARSING */
                for (int j = 0; j < jArrayMessages.length(); j++) {
                    JSONObject jObjMessage = jArrayMessages.getJSONObject(j);
                    tmpNotifMessages[j] = new Message(jObjMessage.getInt("mid"), jObjMessage.getString("text"));
                }

                // initialize ids of notifications
                nids[i] = jObjNotification.getInt("nid");
                // time sets in minutes but we need milliseconds // TODO : закоментить 60, чтоб были секунды, если надо для теста
                notifications[i] = new Notification(jObjNotification.getInt("time") /**60*/ * 1000, nids[i], tmpNotifMessages);
            }

            // Выпилить отсюда, и сделать отдельный метод, который будет устанавливать айдишники в преференсах ? ><
            if (nids != null) {
                //SharedPreferences sharedPreferences = activity.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

                StringBuilder str = new StringBuilder();
                for (int i = 0; i < nids.length; i++) {
                    str.append(nids[i]).append(",");
                }

                sharedPreferences.edit().putInt(NEXT_ID, 0).apply();
                sharedPreferences.edit().putInt(NIDS_SIZE, nids.length).apply();
                sharedPreferences.edit().putString(NIDS, str.toString()).apply();

            }
            ///////////////////////////////////////////////////////////////////////////

            return notifications;

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

    }

}