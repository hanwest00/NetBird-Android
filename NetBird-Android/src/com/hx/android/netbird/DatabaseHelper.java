package com.hx.android.netbird;

import android.content.Context;
import android.database.sqlite.*;

public class DatabaseHelper extends SQLiteOpenHelper{
	private final static int DB_VERSION = 1;
	public final static String DB_NAME = "HX_NetBird";
	public final static String TABLE_MESSAGE = "message";
	public final static String TABLE_ATTACHMENT = "attachment";
	
	public final static String _ID = "_id";
	public final static String FROM_IP = "from_ip";
	public final static String TO_IP = "to_ip";
	public final static String CONTENT = "content";
	public final static String CREATE_DATE = "create_date";
	public final static String FILE_NAME = "file_name";
	public final static String FILE_EXT = "file_ext";
	public final static String SAVE_DIR = "save_dir";
	
	public DatabaseHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
		// TODO Auto-generated constructor stub
	} 

	@Override
	public void onCreate(SQLiteDatabase db) {
		//信息记录表
		db.execSQL("create table message (_id integer primary key autoincrement,from_ip varchar(100) not null,to_ip varchar(100) not null,content text,create_date datetime not null)");
		//文件记录表
		db.execSQL("create table attachment (_id integer primary key autoincrement,from_ip varchar(100) not null,to_ip varchar(100) not null,file_name varchar(100) not null,file_ext varchar(100) not null,save_dir varchar(100) not null,create_date datetime not null)");
		//信息记录表索引
		db.execSQL("create index msg_idx on message(create_date,from_ip,to_ip)");
		db.execSQL("create index attach_idx on attachment(create_date,from_ip,to_ip,file_ext)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		
	}
}
