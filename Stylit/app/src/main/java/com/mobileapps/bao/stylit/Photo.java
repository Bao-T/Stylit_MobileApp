package com.mobileapps.bao.stylit;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class Photo implements Parcelable,Serializable {
    private int farm,ispublic,isfriend,isfamily;
    private String id,owner,secret,server,title;
    public Photo(String id, String owner, String secret, String server, int farm, String title, int ispublic, int isfriend, int isfamily)
    {
        this.id=id;
        this.owner=owner;
        this.secret=secret;
        this.server=server;
        this.farm=farm;
        this.title=title;
        this.ispublic=ispublic;
        this.isfriend=isfriend;
        this.isfamily=isfamily;
    }

    protected Photo(Parcel in) {
        farm = in.readInt();
        ispublic = in.readInt();
        isfriend = in.readInt();
        isfamily = in.readInt();
        id = in.readString();
        owner = in.readString();
        secret = in.readString();
        server = in.readString();
        title = in.readString();
    }

    public static final Creator<Photo> CREATOR = new Creator<Photo>() {
        @Override
        public Photo createFromParcel(Parcel in) {
            return new Photo(in);
        }

        @Override
        public Photo[] newArray(int size) {
            return new Photo[size];
        }
    };

    public int getFarm() {
        return farm;
    }

    public int getIsfamily() {
        return isfamily;
    }

    public int getIsfriend() {
        return isfriend;
    }

    public int getIspublic() {
        return ispublic;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getSecret() {
        return secret;
    }

    public String getServer() {
        return server;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(farm);
        parcel.writeInt(ispublic);
        parcel.writeInt(isfriend);
        parcel.writeInt(isfamily);
        parcel.writeString(id);
        parcel.writeString(owner);
        parcel.writeString(secret);
        parcel.writeString(server);
        parcel.writeString(title);
    }
}
