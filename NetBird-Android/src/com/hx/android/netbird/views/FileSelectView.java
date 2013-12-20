package com.hx.android.netbird.views;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.HorizontalScrollView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;
import android.widget.EditText;

import java.io.*;
import java.lang.reflect.Field;
import java.security.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.hx.android.netbird.R;
import com.hx.android.netbird.R.drawable;

public class FileSelectView extends LinearLayout {
	public final static String FILE_PIC = "filePic";
	public final static String FILE_NAME = "fileName";
	public final static String FILE_FULL_PATH = "fileFullPath";
	private ListView fileList;
	//private Button editBtn;
	//private Button selectBtn;
	//private Button delBtn;
	private Button fileNavBack;
	private EditText fileNav;
	private OnFileOrDirSelected fileDirSelected;
	private List<File> selectedFiles;//定义为List为下个版本多文件发送做准备
	private String currDir;
	private Class<drawable> drawableClass;
	private int unknowFile;
	private int dir;
	private int mainWidth;
	private int mainHeight;

	public FileSelectView(Context context, int width, int height)
			throws IllegalArgumentException, SecurityException,
			IllegalAccessException, NoSuchFieldException {
		super(context);
		this.mainWidth = width;
		this.mainHeight = height;
		
		this.selectedFiles = new ArrayList<File>();

		LayoutInflater layoutInflater = (LayoutInflater) context
				.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
		layoutInflater.inflate(R.layout.file_select_view, this, true);
		this.iniViews();
		this.bindFileToFileListView("/");
		LayoutParams lp = new LayoutParams(this.mainWidth, this.mainHeight);
		this.setLayoutParams(lp);

		/*this.selectBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if (fileDirSelected != null)
					fileDirSelected.selected(selectedFiles, currDir);
			}

		});*/

		this.fileNavBack.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (!currDir.equals("/"))
					try {
						bindFileToFileListView(currDir.substring(0,
								currDir.lastIndexOf("/")));
					} catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SecurityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchFieldException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		});

		this.fileList.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				TextView tmp = (TextView) arg1
						.findViewById(R.id.file_name);
				File f = new File(currDir + "/" + tmp.getText().toString());
				if (f.isDirectory()) {
					try {
						bindFileToFileListView(f.getAbsolutePath());
					} catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SecurityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchFieldException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return;
				}
				
				if(f.isFile())
				{
					selectedFiles.add(f);
					fileDirSelected.selected(selectedFiles, currDir);
				}
			}
		});
	}

	private void iniViews() throws IllegalArgumentException, SecurityException,
			IllegalAccessException, NoSuchFieldException {
		this.drawableClass = drawable.class;
		this.unknowFile = this.drawableClass.getDeclaredField("file").getInt(
				this.drawableClass);
		this.dir = this.drawableClass.getDeclaredField("folder").getInt(
				this.drawableClass);
		//this.editBtn = (Button) this.findViewById(R.id.file_start_edit_btn);
		//this.selectBtn = (Button) this.findViewById(R.id.select_file_or_dir);
		//this.delBtn = (Button) this.findViewById(R.id.file_del_btn);
		this.fileNavBack = (Button) this.findViewById(R.id.file_nav_back);
		this.fileNav = (EditText) this.findViewById(R.id.file_nav);
		this.fileNav.setWidth((int) (this.mainWidth * 0.825));
		this.fileList = (ListView) this.findViewById(R.id.file_list);
		this.fileList.setCacheColorHint(Color.alpha(100));
		this.fileList.setLayoutParams(new LayoutParams(this.mainWidth,
				(int)(this.mainHeight * 0.92)));
	}

	private void bindFileToFileListView(String selectDir)
			throws IllegalArgumentException, SecurityException,
			IllegalAccessException, NoSuchFieldException {
		if (selectDir.equals(""))
			selectDir = "/";
		this.currDir = selectDir;
		this.fileNav.setText(this.currDir);
		File dir = new File(this.currDir);
		File[] files = dir.listFiles();
		List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		if (files != null)
			for (File f : files) {
				String name = f.getName();
				Map<String, Object> map = new HashMap<String, Object>();
				if (f.isDirectory()) {
					map.put(FILE_PIC, this.getImageResourceByFileExt(""));
					map.put(FILE_FULL_PATH, f.getAbsolutePath());
				} else {
					int p = name.lastIndexOf(".");
					if (p > 0) {
						map.put(FILE_PIC, this.getImageResourceByFileExt(name
								.substring(p + 1).toLowerCase()));
					} else
						map.put(FILE_PIC, this.unknowFile);
					
					StringBuilder sb = new StringBuilder(String.valueOf(f.length() / 1024));
					sb.append("KB ");
					sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(f.lastModified())));
					map.put(FILE_FULL_PATH, sb.toString());
				}
				map.put(FILE_NAME, name);

				data.add(map);
			}
		else
			data.add(new HashMap<String, Object>());

		SimpleAdapter adp = new SimpleAdapter(this.getContext(), data,
				R.layout.file_list_view, new String[] { FILE_PIC, FILE_NAME,
						FILE_FULL_PATH }, new int[] { R.id.file_pic,
						R.id.file_name, R.id.file_full_path });
		adp.setViewBinder(new ViewBinder() {
			public boolean setViewValue(View view, Object data,
					String textRepresentation) {
				if (view instanceof ImageView && data instanceof Integer) {
					ImageView img = (ImageView) view;
					img.setImageResource(Integer.parseInt(data.toString()));
					return true;
				}
				return false;
			}
		});
		this.fileList.setAdapter(adp);
	}

	public int getImageResourceByFileExt(String ext)
			throws IllegalArgumentException, IllegalAccessException,
			SecurityException, NoSuchFieldException {
		Field field = null;
		if (ext == null || ext.equals(""))
			return this.dir;
		try {
			field = this.drawableClass.getDeclaredField(ext);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			return this.unknowFile;
		}
		return field.getInt(this.drawableClass);
	}

	public void setOnFileOrDirSelected(OnFileOrDirSelected fileOrDirSelected) {
		this.fileDirSelected = fileOrDirSelected;
	}

	public static class OnFileOrDirSelected {
		public void selected(List<File> files, String dir) {

		}
	}

	public String StackToString(Stack<String> path) {
		StringBuilder sb = new StringBuilder();
		for (String s : path)
			sb.append(s);
		return sb.toString();
	}
}
