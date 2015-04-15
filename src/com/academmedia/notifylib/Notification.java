package com.academmedia.notifylib;


import android.os.Parcel;
import android.os.Parcelable;

/** ===================================================================== */
public class Notification implements Parcelable {
    long delay;
    int nid;
    Message[] messages;

    public Notification(long delay, int nid, Message[] messages) {
        this.delay = delay;
        this.nid = nid;
        this.messages = messages;
    }

    public Notification(Parcel in) {
        delay = in.readLong();
        nid = in.readInt();

        messages = new Message[in.readInt()];
        in.readTypedArray(messages, Message.CREATOR);

    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(delay);
        parcel.writeInt(nid);
        parcel.writeInt(messages.length);
        parcel.writeTypedArray(messages, 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Notification> CREATOR
            = new Parcelable.Creator<Notification>() {
        public Notification createFromParcel(Parcel in) {
            return new Notification(in);
        }

        public Notification[] newArray(int size) {
            return new Notification[size];
        }
    };

}