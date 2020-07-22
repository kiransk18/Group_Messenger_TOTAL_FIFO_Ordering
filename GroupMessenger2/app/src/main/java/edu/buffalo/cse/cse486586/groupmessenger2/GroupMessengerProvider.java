package edu.buffalo.cse.cse486586.groupmessenger2;

import android.database.MatrixCursor;

import android.content.ContentValues;
import java.io.FileOutputStream;
import android.content.Context;

import android.net.Uri;
import android.util.Log;

import java.io.FileInputStream;
import android.database.Cursor;
import java.io.IOException;
import android.content.ContentProvider;


public class GroupMessengerProvider extends ContentProvider {

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        return 0;
    }

    @Override
    public String getType(Uri uri) {

        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        String filename = values.get("key").toString();
        String data = values.get("value").toString();
        FileOutputStream op;
        try
        {
            op = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            op.write(data.getBytes());
            op.close();
        }
        catch(IOException ex){
            Log.e("Exception", "File Write Failed");
        }

        Log.v("insert", values.toString());

        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        // Referred PA1 Code
        String temp = "";
        try {
            FileInputStream fin = getContext().openFileInput(selection);
            int c;
            while( (c = fin.read()) != -1){
                temp = temp + (char)c;
            }

            fin.close();
            Log.e(selection, temp);
        }
        catch(Exception ex)
        {
            Log.e("fileReadException","Exception occured reading msg from file");
        }

        //Referred https://developer.android.com/reference/android/database/MatrixCursor
        MatrixCursor cursor = new MatrixCursor(new String[] {"key", "value"});
        cursor.newRow()
                .add("key", selection)
                .add("value", temp);
        if(cursor != null)
            return cursor;
        Log.v("query", selection);
        return null;
    }
}
