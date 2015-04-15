package com.academmedia.notifylib;


import android.os.Parcel;
import android.os.Parcelable;

/** ===================================================================== */
public class Message implements Parcelable {
    int mid;
    String text;

    public Message(int mid, String text) {
        this.mid = mid;
        this.text = text;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(mid);
        parcel.writeString(text);
    }

    public static final Parcelable.Creator<Message> CREATOR = new Parcelable.Creator<Message>() {

        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        public Message[] newArray(int size) {
            return new Message[size];
        }

    };

    public Message(Parcel in) {
        mid = in.readInt();
        text = in.readString();
    }

}