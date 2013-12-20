package com.hx.android.netbird;

import java.text.SimpleDateFormat;
import java.util.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;

import com.hx.android.netbird.R.drawable;
import com.hx.android.netbird.R.id;

import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout.LayoutParams;

public class NetBird extends Activity {
	public final static int port = 6993;// 广播端口
	public final static String EXTRA_MSG = "MSG";// 消息头
	public final static String EXTRA_MSG_LIST = "MSGLIST";// 多条消息头
	public final static String EXTRA_IP = "IP";
	public final static int RESULT_MSG_INCREASES = 10101;// 消息增长flag
	public final static int MSG_COUNT_ID = 0x12121122;// 显示消息数目的TextView的ID
	public static DatabaseHelper dbHelper;

	private ListView userListView;// IP列表view
	private AlertDialog quitDlg;// 退出对话框
	private static String bdAddStr = "224.0.0.1";// 广播的IP
	private static String LocalIP;// 当前IP
	private MulticastSocket ds;// 广播用的多播套接字类
	private DatagramSocket allMsgListen;// 用于监听信息的upd套接字
	private Map<String, ArrayList<String>> msgList;// IP与消息的字典
	private List<Map<String, String>> data;// 用于绑定userListView数据的List
	private SimpleAdapter dataAdapter;
	private Thread thread;// 监听广播的线程
	private Thread msgThread;// 监听消息的线程
	private boolean listenOpen;// thread进程开关
	private boolean msgListenOpen;// msgThread进程开关
	private Handler userListHandler;// 其他线程调用UI线程的handler

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		dbHelper = new DatabaseHelper(this);
		this.msgList = new HashMap<String, ArrayList<String>>();
		this.userListView = (ListView) this.findViewById(id.UserList);
		this.data = new ArrayList<Map<String, String>>();
		this.userListHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case RESULT_OK:
					dataAdapter = new SimpleAdapter(NetBird.this, data,
							R.layout.user_list_view, new String[] { "UserIp" },
							new int[] { R.id.UserIp });
					userListView.setAdapter(dataAdapter);
					break;
				case RESULT_MSG_INCREASES:
					LinearLayout currLayout = findLinearLayoutByIp(msg.obj
							.toString());
					if (currLayout == null)
						break;

					((TextView) currLayout.findViewById(R.id.UserIp))
							.setWidth((int) (getWindowManager()
									.getDefaultDisplay().getWidth() * 0.8f));
					TextView tv;
					if (currLayout.findViewById(MSG_COUNT_ID) == null) {
						tv = new TextView(NetBird.this);
						LayoutParams lp = new LayoutParams(
								LayoutParams.WRAP_CONTENT,
								LayoutParams.WRAP_CONTENT);
						lp.gravity = Gravity.CENTER_VERTICAL;
						tv.setLayoutParams(lp);
						tv.setId(MSG_COUNT_ID);
						tv.setTextColor(Color.WHITE);
						tv.setTextSize(14.0f);
						tv.setGravity(Gravity.CENTER);
						tv.setHeight(20);
						tv.setWidth(20);
						tv.setText("1");
						tv.setBackgroundResource(R.drawable.red_circle);
						currLayout.addView(tv);
						break;
					}

					tv = (TextView) currLayout.findViewById(MSG_COUNT_ID);
					tv.setText(String.valueOf(Integer.parseInt(tv.getText()
							.toString()) + 1));
					break;

				}
			}
		};

		this.userListView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				// TODO Auto-generated method stub
				/*
				 * //String ss =
				 * ((TextView)arg1.findViewById(R.id.UserIp)).getText
				 * ().toString();
				 * 
				 * @SuppressWarnings("unchecked") String ss =
				 * ((HashMap<String,String
				 * >)arg0.getItemAtPosition(arg2)).get("UserIp").toString();
				 * AlertDialog.Builder bd = new AlertDialog.Builder(context);
				 * bd.setMessage(ss); bd.setTitle("ip");
				 * bd.setNeutralButton("Cancel", new
				 * DialogInterface.OnClickListener(){ public void
				 * onClick(DialogInterface dialog, int which) { // TODO
				 * Auto-generated method stub dialog.cancel(); } }); bd.show();
				 */
				if (!DoseWifiOk(NetBird.this)) {
					NoWifiPopup();
					return;
				}

				String ipStr = ((TextView) arg1.findViewById(R.id.UserIp))
						.getText().toString();
				Intent intent = new Intent();
				intent.setClass(NetBird.this, MessageView.class);
				Bundle extras = new Bundle();
				extras.putString(EXTRA_IP, ipStr);
				ArrayList list = new ArrayList();
				list.add(msgList.get(ipStr));
				extras.putParcelableArrayList(EXTRA_MSG_LIST, new ArrayList(
						list));
				intent.putExtras(extras);
				// intent.setFlags(intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(intent);

				LinearLayout currLayout = findLinearLayoutByIp(ipStr);
				currLayout.removeView(currLayout.findViewById(MSG_COUNT_ID));
				msgList.get(ipStr).clear();
			}
		});

		try {
			this.ds = new MulticastSocket(port);
			this.ds.setSoTimeout(1000);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			this.allMsgListen = new DatagramSocket(MessageView.PORT);
			this.allMsgListen.setSoTimeout(1000);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if (!DoseWifiOk(this))
			this.NoWifiPopup();
		else {
			try {
				this.listenOpen = true;
				this.msgListenOpen = true;
				this.DoUdpListen();
				this.doAllMsgListen();
				
				this.OnlineBroadcast();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/*
		 * final TextView text01 = (TextView)this.findViewById(id.TextView01);
		 * Button btn01 = (Button)this.findViewById(id.Btn01); this.webView =
		 * (WebView)this.findViewById(id.WebView01);
		 * this.webView.setWebViewClient(new WebViewClient(){ public boolean
		 * shouldOverrideUrlLoading(WebView view, String url) {
		 * view.loadUrl(url); return true; } });
		 * 
		 * btn01.setOnClickListener(new OnClickListener(){ public void
		 * onClick(View v) { // TODO Auto-generated method stub
		 * if(text01.getText() != "") { ViewWebPage((String)text01.getText()); }
		 * } });
		 */
	}

	// 捕获按键
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// When the user center presses, let them pick a contact.
			this.popupQuitDialog();
			return true;
		}
		return false;
	}

	// 当前activity菜单
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem item = menu.add("Quit");
		item.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			public boolean onMenuItemClick(MenuItem item) {
				// TODO Auto-generated method stub
				popupQuitDialog();
				return true;
			}
		});
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.OnlineBroadcast();
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		this.OnlineBroadcast();
	}

	@Override
	protected void onStop() {
		super.onStop();
		this.OfflineBroadcast();
	}

	private void popupQuitDialog() {
		if (this.quitDlg == null) {
			Builder quitDlgBud = new Builder(NetBird.this);
			quitDlgBud.setTitle("Notice");
			quitDlgBud.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// TODO Auto-generated method stub
							System.exit(0);
						}
					});
			quitDlgBud.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// TODO Auto-generated method stub
							quitDlg.dismiss();
						}
					});
			this.quitDlg = quitDlgBud.create();
			TextView tv = new TextView(NetBird.this);
			tv.setTextSize(16.0f);
			tv.setText("Quit?");
			tv.setWidth(100);
			tv.setHeight(100);
			tv.setGravity(Gravity.CENTER);
			this.quitDlg.setView(tv);
		}
		this.quitDlg.show();
	}

	private LinearLayout findLinearLayoutByIp(String ip) {
		for (int i = 0; i < userListView.getChildCount(); i++) {
			LinearLayout currLayout = (LinearLayout) userListView.getChildAt(i);
			if (((TextView) currLayout.findViewById(R.id.UserIp)).getText()
					.toString().equals(ip))
				return currLayout;
		}
		return null;
	}

	private void OnlineBroadcast() {
		StringBuffer sb = new StringBuffer("IP:");
		try {
			sb.append(GetLocalIp());
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		this.DoBroadcast(sb.toString());
	}

	private void OfflineBroadcast() {
		StringBuffer sb = new StringBuffer("OFF:");
		try {
			sb.append(GetLocalIp());
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		this.DoBroadcast(sb.toString());
	}

	private void DoBroadcast(String msg) {
		try {
			byte[] buffer = msg.getBytes();
			InetAddress bdAdd = InetAddress.getByName(bdAddStr);
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length,
					bdAdd, port);
			ds.send(dp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// 得到本地IP(内网) reload参数用于控制是否从新获取IP
	public static String GetLocalIp(boolean reload) throws SocketException {
		if (!reload && LocalIP != null)
			return LocalIP;
		Enumeration<NetworkInterface> nis = NetworkInterface
				.getNetworkInterfaces();
		while (nis.hasMoreElements()) {
			NetworkInterface ni = nis.nextElement();
			Enumeration<InetAddress> ips = ni.getInetAddresses();
			while (ips.hasMoreElements()) {
				InetAddress ip = ips.nextElement();
				if (ip instanceof Inet4Address) {
					if (ip.getHostAddress().equals("127.0.0.1"))
						continue;
					LocalIP = ip.getHostAddress();
					return LocalIP;
				}
			}
		}
		return "";
	}

	public void PlaySound(int soundId, int loop) {
		SoundPool sp = new SoundPool(1,
				android.media.AudioManager.STREAM_MUSIC, 100);
		AudioManager mgr = (AudioManager) this
				.getSystemService(Context.AUDIO_SERVICE);
		float streamVolumeCurrent = mgr
				.getStreamVolume(AudioManager.STREAM_MUSIC);
		float streamVolumeMax = mgr
				.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		float volume = streamVolumeCurrent / streamVolumeMax;// 得到音量的大小

		sp.play(soundId, volume, volume, 1, loop, 1f);
	}

	public static void NetWorkErrorPopup() {

	}

	public static String GetLocalIp() throws SocketException {
		return GetLocalIp(false);
	}

	// 监听消息
	public void doAllMsgListen() throws IOException {
		this.msgThread = new Thread(new Runnable() {
			public void run() {
				while (msgListenOpen) {
					try {
						byte[] buffer = new byte[MessageView.MSG_MAX_LEN];
						DatagramPacket dp = new DatagramPacket(buffer,
								buffer.length);
						allMsgListen.receive(dp);
						if (dp.getLength() > 4) {
							String sendIP = dp.getAddress().getHostAddress();
							PlaySound(R.raw.notice, 1);
							String msgStr = new String(buffer, 0,
									dp.getLength());
							// 如果发送IP是当前对话的IP,广播消息到MessageView,否则加入消息List
							if (sendIP.equals(MessageView.currIp)) {
								Intent intent = new Intent();
								intent.setAction("action.newMessage");
								intent.putExtra(EXTRA_MSG, msgStr);
								sendBroadcast(intent);
							} else {
								msgList.get(sendIP).add(msgStr);
								Message msg = userListHandler.obtainMessage();
								msg.what = RESULT_MSG_INCREASES;
								msg.obj = dp.getAddress().getHostAddress();
								userListHandler.sendMessage(msg);
							}
							SimpleDateFormat df = new SimpleDateFormat();

							// todo:insert new data to database,hanx 20131128
							AddMessageToDB(sendIP, GetLocalIp(), msgStr,
									df.format(new Date()));
						}
						Thread.sleep(100);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						// e.printStackTrace();
						continue;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		});
		this.msgThread.start();
	}

	// 监听广播消息(上线下线,收到上线)
	private void DoUdpListen() throws IOException {
		ds.joinGroup(InetAddress.getByName(bdAddStr));
		this.thread = new Thread(new Runnable() {
			public void run() {
				while (listenOpen) {
					byte[] buffer = new byte[1024];
					DatagramPacket dp = new DatagramPacket(buffer,
							buffer.length);
					try {
						ds.receive(dp);
						if (dp.getLength() < 3)
							continue;

						// online or receive
						if (buffer[0] == 73 && buffer[1] == 80) {
							String ip = new String(buffer, 0, dp.getLength())
									.substring(3).replace(":", "");
							if (!msgList.containsKey(ip))
								msgList.put(ip, new ArrayList<String>());
							Map<String, String> hash = new HashMap<String, String>();
							hash.put("UserIp", ip);
							if (!data.contains(hash)) {
								data.add(hash);
								Message m = userListHandler.obtainMessage();
								m.what = RESULT_OK;
								userListHandler.sendMessage(m);
								if (buffer[2] == 82)
									continue;
								StringBuilder sb = new StringBuilder("IPR:");
								sb.append(GetLocalIp());
								byte[] sendReceiveOnlineMsg = sb.toString()
										.getBytes();
								DatagramPacket rdp = new DatagramPacket(
										sendReceiveOnlineMsg,
										sendReceiveOnlineMsg.length,
										Inet4Address.getByName(ip), port);
								ds.send(rdp);
							}
							continue;
						}

						// offline
						if (buffer[0] == 79 && buffer[1] == 70
								&& buffer[2] == 70) {
							String ip = new String(buffer, 0, dp.getLength())
									.substring(4);
							if (!msgList.containsKey(ip))
								msgList.put(ip, new ArrayList<String>());
							Map<String, String> hash = new HashMap<String, String>();
							hash.put("UserIp", ip);
							if (!data.contains(hash)) {
								data.remove(hash);
								Message m = userListHandler.obtainMessage();
								m.what = RESULT_OK;
								userListHandler.sendMessage(m);
							}
						}
						Thread.sleep(100);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						// e.printStackTrace();
						continue;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
		this.thread.start();
	}

	public static boolean DoseWifiOk(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo nf = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (nf != null)
			return nf.isAvailable();
		return false;
	}

	// wifi连接不可用Dialog
	public void NoWifiPopup() {
		new AlertDialog.Builder(this)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Field f = null;
						try {
							// 使用反射阻止AlertDialog对话框关闭
							f = dialog.getClass().getSuperclass()
									.getDeclaredField("mShowing");
							f.setAccessible(true);
							f.setBoolean(dialog, false);
						} catch (SecurityException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (NoSuchFieldException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IllegalArgumentException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						if (!DoseWifiOk(NetBird.this)) {
							return;
						}

						try {
							// 使用反射允许AlertDialog对话框关闭
							f.set(dialog, true);
						} catch (IllegalArgumentException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						dialog.dismiss();
						// 从新发出上线广播
						listenOpen = true;
						msgListenOpen = true;
						
						try {
							DoUdpListen();
							doAllMsgListen();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						OnlineBroadcast();
					}
				})
				.setNeutralButton("Setting",
						new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog,
									int which) {
								try {
									// 使用反射阻止AlertDialog对话框关闭
									Field f = dialog.getClass().getSuperclass()
											.getDeclaredField("mShowing");
									f.setAccessible(true);
									f.setBoolean(dialog, false);
								} catch (SecurityException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (NoSuchFieldException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IllegalArgumentException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IllegalAccessException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								OpenWifiSetting(NetBird.this);
							}
						}).setMessage("wifi error!").create().show();

	}

	// 打开wifi设置界面
	public static void OpenWifiSetting(Context context) {
		if (Build.VERSION.SDK_INT > 14) {
			context.startActivity(new Intent(
					android.provider.Settings.ACTION_SETTINGS));
		} else {
			context.startActivity(new Intent(
					android.provider.Settings.ACTION_WIRELESS_SETTINGS));
		}
	}

	public static long AddMessageToDB(String from, String to, String content,
			String date) {
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.FROM_IP, from);
		values.put(DatabaseHelper.TO_IP, to);
		values.put(DatabaseHelper.CONTENT, content);
		values.put(DatabaseHelper.CREATE_DATE, date);
		return db.insert(DatabaseHelper.TABLE_MESSAGE, null, values);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		this.listenOpen = false;
		this.msgListenOpen = false;
		if (this.ds != null)
			this.ds.close();
		if (this.allMsgListen != null)
			this.allMsgListen.close();
		if (dbHelper != null)
			dbHelper.close();
	}
	/*
	 * private void ViewWebPage(String url) { if(this.webView == null) return;
	 * this.webView.loadUrl(url); }
	 */
}