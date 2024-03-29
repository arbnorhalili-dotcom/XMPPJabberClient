package com.KDJStudios.XMPPJabberClient.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.security.KeyChain;
import android.support.annotation.BoolRes;
import android.support.annotation.IntegerRes;
import android.support.v4.app.RemoteInput;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.KDJStudios.XMPPJabberClient.Config;
import com.KDJStudios.XMPPJabberClient.R;
import com.KDJStudios.XMPPJabberClient.crypto.OmemoSetting;
import com.KDJStudios.XMPPJabberClient.crypto.PgpDecryptionService;
import com.KDJStudios.XMPPJabberClient.crypto.PgpEngine;
import com.KDJStudios.XMPPJabberClient.crypto.axolotl.AxolotlService;
import com.KDJStudios.XMPPJabberClient.crypto.axolotl.FingerprintStatus;
import com.KDJStudios.XMPPJabberClient.crypto.axolotl.XmppAxolotlMessage;
import com.KDJStudios.XMPPJabberClient.entities.Account;
import com.KDJStudios.XMPPJabberClient.entities.Blockable;
import com.KDJStudios.XMPPJabberClient.entities.Bookmark;
import com.KDJStudios.XMPPJabberClient.entities.Contact;
import com.KDJStudios.XMPPJabberClient.entities.Conversation;
import com.KDJStudios.XMPPJabberClient.entities.DownloadableFile;
import com.KDJStudios.XMPPJabberClient.entities.Message;
import com.KDJStudios.XMPPJabberClient.entities.MucOptions;
import com.KDJStudios.XMPPJabberClient.entities.MucOptions.OnRenameListener;
import com.KDJStudios.XMPPJabberClient.entities.Presence;
import com.KDJStudios.XMPPJabberClient.entities.PresenceTemplate;
import com.KDJStudios.XMPPJabberClient.entities.Roster;
import com.KDJStudios.XMPPJabberClient.entities.ServiceDiscoveryResult;
import com.KDJStudios.XMPPJabberClient.entities.Transferable;
import com.KDJStudios.XMPPJabberClient.entities.TransferablePlaceholder;
import com.KDJStudios.XMPPJabberClient.generator.AbstractGenerator;
import com.KDJStudios.XMPPJabberClient.generator.IqGenerator;
import com.KDJStudios.XMPPJabberClient.generator.MessageGenerator;
import com.KDJStudios.XMPPJabberClient.generator.PresenceGenerator;
import com.KDJStudios.XMPPJabberClient.http.HttpConnectionManager;
import com.KDJStudios.XMPPJabberClient.http.AesGcmURLStreamHandlerFactory;
import com.KDJStudios.XMPPJabberClient.parser.AbstractParser;
import com.KDJStudios.XMPPJabberClient.parser.IqParser;
import com.KDJStudios.XMPPJabberClient.parser.MessageParser;
import com.KDJStudios.XMPPJabberClient.parser.PresenceParser;
import com.KDJStudios.XMPPJabberClient.persistance.DatabaseBackend;
import com.KDJStudios.XMPPJabberClient.persistance.FileBackend;
import com.KDJStudios.XMPPJabberClient.ui.SettingsActivity;
import com.KDJStudios.XMPPJabberClient.ui.UiCallback;
import com.KDJStudios.XMPPJabberClient.utils.ConversationsFileObserver;
import com.KDJStudios.XMPPJabberClient.utils.CryptoHelper;
import com.KDJStudios.XMPPJabberClient.utils.ExceptionHelper;
import com.KDJStudios.XMPPJabberClient.utils.MimeUtils;
import com.KDJStudios.XMPPJabberClient.utils.OnPhoneContactsLoadedListener;
import com.KDJStudios.XMPPJabberClient.utils.PRNGFixes;
import com.KDJStudios.XMPPJabberClient.utils.PhoneHelper;
import com.KDJStudios.XMPPJabberClient.utils.QuickLoader;
import com.KDJStudios.XMPPJabberClient.utils.ReplacingSerialSingleThreadExecutor;
import com.KDJStudios.XMPPJabberClient.utils.ReplacingTaskManager;
import com.KDJStudios.XMPPJabberClient.utils.Resolver;
import com.KDJStudios.XMPPJabberClient.utils.SerialSingleThreadExecutor;
import com.KDJStudios.XMPPJabberClient.utils.WakeLockHelper;
import com.KDJStudios.XMPPJabberClient.xml.Namespace;
import com.KDJStudios.XMPPJabberClient.utils.XmppUri;
import com.KDJStudios.XMPPJabberClient.xml.Element;
import com.KDJStudios.XMPPJabberClient.xmpp.OnBindListener;
import com.KDJStudios.XMPPJabberClient.xmpp.OnContactStatusChanged;
import com.KDJStudios.XMPPJabberClient.xmpp.OnIqPacketReceived;
import com.KDJStudios.XMPPJabberClient.xmpp.OnKeyStatusUpdated;
import com.KDJStudios.XMPPJabberClient.xmpp.OnMessageAcknowledged;
import com.KDJStudios.XMPPJabberClient.xmpp.OnMessagePacketReceived;
import com.KDJStudios.XMPPJabberClient.xmpp.OnPresencePacketReceived;
import com.KDJStudios.XMPPJabberClient.xmpp.OnStatusChanged;
import com.KDJStudios.XMPPJabberClient.xmpp.OnUpdateBlocklist;
import com.KDJStudios.XMPPJabberClient.xmpp.Patches;
import com.KDJStudios.XMPPJabberClient.xmpp.XmppConnection;
import com.KDJStudios.XMPPJabberClient.xmpp.chatstate.ChatState;
import com.KDJStudios.XMPPJabberClient.xmpp.forms.Data;
import com.KDJStudios.XMPPJabberClient.xmpp.jingle.JingleConnectionManager;
import com.KDJStudios.XMPPJabberClient.xmpp.jingle.OnJinglePacketReceived;
import com.KDJStudios.XMPPJabberClient.xmpp.jingle.stanzas.JinglePacket;
import com.KDJStudios.XMPPJabberClient.xmpp.mam.MamReference;
import com.KDJStudios.XMPPJabberClient.xmpp.pep.Avatar;
import com.KDJStudios.XMPPJabberClient.xmpp.stanzas.IqPacket;
import com.KDJStudios.XMPPJabberClient.xmpp.stanzas.MessagePacket;
import com.KDJStudios.XMPPJabberClient.xmpp.stanzas.PresencePacket;
import me.leolin.shortcutbadger.ShortcutBadger;
import rocks.xmpp.addr.Jid;

public class XmppConnectionService extends Service {

	public static final String ACTION_REPLY_TO_CONVERSATION = "reply_to_conversations";
	public static final String ACTION_MARK_AS_READ = "mark_as_read";
	public static final String ACTION_SNOOZE = "snooze";
	public static final String ACTION_CLEAR_NOTIFICATION = "clear_notification";
	public static final String ACTION_DISMISS_ERROR_NOTIFICATIONS = "dismiss_error";
	public static final String ACTION_TRY_AGAIN = "try_again";
	public static final String ACTION_IDLE_PING = "idle_ping";
	public static final String ACTION_GCM_TOKEN_REFRESH = "gcm_token_refresh";
	public static final String ACTION_GCM_MESSAGE_RECEIVED = "gcm_message_received";
	private static final String ACTION_MERGE_PHONE_CONTACTS = "merge_phone_contacts";

	static {
		URL.setURLStreamHandlerFactory(new AesGcmURLStreamHandlerFactory());
	}

	public final CountDownLatch restoredFromDatabaseLatch = new CountDownLatch(1);
	private final SerialSingleThreadExecutor mFileAddingExecutor = new SerialSingleThreadExecutor("FileAdding");
	private final SerialSingleThreadExecutor mVideoCompressionExecutor = new SerialSingleThreadExecutor("VideoCompression");
	private final SerialSingleThreadExecutor mDatabaseWriterExecutor = new SerialSingleThreadExecutor("DatabaseWriter");
	private final SerialSingleThreadExecutor mDatabaseReaderExecutor = new SerialSingleThreadExecutor("DatabaseReader");
	private final SerialSingleThreadExecutor mNotificationExecutor = new SerialSingleThreadExecutor("NotificationExecutor");
	private final ReplacingTaskManager mRosterSyncTaskManager = new ReplacingTaskManager();
	private final IBinder mBinder = new XmppConnectionBinder();
	private final List<Conversation> conversations = new CopyOnWriteArrayList<>();
	private final IqGenerator mIqGenerator = new IqGenerator(this);
	private final List<String> mInProgressAvatarFetches = new ArrayList<>();
	private final HashSet<Jid> mLowPingTimeoutMode = new HashSet<>();
	private final OnIqPacketReceived mDefaultIqHandler = (account, packet) -> {
		if (packet.getType() != IqPacket.TYPE.RESULT) {
			Element error = packet.findChild("error");
			String text = error != null ? error.findChildContent("text") : null;
			if (text != null) {
				Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received iq error - " + text);
			}
		}
	};
	public DatabaseBackend databaseBackend;
	private ReplacingSerialSingleThreadExecutor mContactMergerExecutor = new ReplacingSerialSingleThreadExecutor(true);
	private long mLastActivity = 0;
	private ContentObserver contactObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			Intent intent = new Intent(getApplicationContext(),
					XmppConnectionService.class);
			intent.setAction(ACTION_MERGE_PHONE_CONTACTS);
			startService(intent);
		}
	};
	private FileBackend fileBackend = new FileBackend(this);
	private MemorizingTrustManager mMemorizingTrustManager;
	private NotificationService mNotificationService = new NotificationService(this);
	private ShortcutService mShortcutService = new ShortcutService(this);
	private AtomicBoolean mInitialAddressbookSyncCompleted = new AtomicBoolean(false);
	private AtomicBoolean mForceForegroundService = new AtomicBoolean(false);
	private OnMessagePacketReceived mMessageParser = new MessageParser(this);
	private OnPresencePacketReceived mPresenceParser = new PresenceParser(this);
	private IqParser mIqParser = new IqParser(this);
	private MessageGenerator mMessageGenerator = new MessageGenerator(this);
	public OnContactStatusChanged onContactStatusChanged = (contact, online) -> {
		Conversation conversation = find(getConversations(), contact);
		if (conversation != null) {
			if (online) {
				if (contact.getPresences().size() == 1) {
					sendUnsentMessages(conversation);
				}
			}
		}
	};
	private PresenceGenerator mPresenceGenerator = new PresenceGenerator(this);
	private List<Account> accounts;
	private JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(
			this);
	private final OnJinglePacketReceived jingleListener = new OnJinglePacketReceived() {

		@Override
		public void onJinglePacketReceived(Account account, JinglePacket packet) {
			mJingleConnectionManager.deliverPacket(account, packet);
		}
	};
	private HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager(
			this);
	private AvatarService mAvatarService = new AvatarService(this);
	private MessageArchiveService mMessageArchiveService = new MessageArchiveService(this);
	//private PushManagementService mPushManagementService = new PushManagementService(this);
	private OnConversationUpdate mOnConversationUpdate = null;
	private final ConversationsFileObserver fileObserver = new ConversationsFileObserver(
			Environment.getExternalStorageDirectory().getAbsolutePath()
	) {
		@Override
		public void onEvent(int event, String path) {
			markFileDeleted(path);
		}
	};
	private final OnMessageAcknowledged mOnMessageAcknowledgedListener = new OnMessageAcknowledged() {

		@Override
		public void onMessageAcknowledged(Account account, String uuid) {
			for (final Conversation conversation : getConversations()) {
				if (conversation.getAccount() == account) {
					Message message = conversation.findUnsentMessageWithUuid(uuid);
					if (message != null) {
						markMessage(message, Message.STATUS_SEND);
					}
				}
			}
		}
	};
	private int convChangedListenerCount = 0;
	private OnShowErrorToast mOnShowErrorToast = null;
	private int showErrorToastListenerCount = 0;
	private int unreadCount = -1;
	private OnAccountUpdate mOnAccountUpdate = null;
	private OnCaptchaRequested mOnCaptchaRequested = null;
	private int accountChangedListenerCount = 0;
	private int captchaRequestedListenerCount = 0;
	private OnRosterUpdate mOnRosterUpdate = null;
	private OnUpdateBlocklist mOnUpdateBlocklist = null;
	private int updateBlocklistListenerCount = 0;
	private int rosterChangedListenerCount = 0;
	private OnMucRosterUpdate mOnMucRosterUpdate = null;
	private int mucRosterChangedListenerCount = 0;
	private OnKeyStatusUpdated mOnKeyStatusUpdated = null;
	private final OnBindListener mOnBindListener = new OnBindListener() {

		@Override
		public void onBind(final Account account) {
			synchronized (mInProgressAvatarFetches) {
				for (Iterator<String> iterator = mInProgressAvatarFetches.iterator(); iterator.hasNext(); ) {
					final String KEY = iterator.next();
					if (KEY.startsWith(account.getJid().asBareJid() + "_")) {
						iterator.remove();
					}
				}
			}
			boolean needsUpdating = account.setOption(Account.OPTION_LOGGED_IN_SUCCESSFULLY, true);
			needsUpdating |= account.setOption(Account.OPTION_HTTP_UPLOAD_AVAILABLE, account.getXmppConnection().getFeatures().httpUpload(0));
			if (needsUpdating) {
				databaseBackend.updateAccount(account);
			}
			account.getRoster().clearPresences();
			mJingleConnectionManager.cancelInTransmission();
			fetchRosterFromServer(account);
			fetchBookmarks(account);
			final boolean flexible = account.getXmppConnection().getFeatures().flexibleOfflineMessageRetrieval();
			final boolean catchup = getMessageArchiveService().inCatchup(account);
			if (flexible && catchup) {
				sendIqPacket(account, mIqGenerator.purgeOfflineMessages(), (acc, packet) -> {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						Log.d(Config.LOGTAG, acc.getJid().asBareJid() + ": successfully purged offline messages");
					}
				});
			}
			sendPresence(account);
			/*if (mPushManagementService.available(account)) {
				mPushManagementService.registerPushTokenOnServer(account);
			}*/
			connectMultiModeConversations(account);
			syncDirtyContacts(account);
		}
	};
	private int keyStatusUpdatedListenerCount = 0;
	private AtomicLong mLastExpiryRun = new AtomicLong(0);
	private SecureRandom mRandom;
	private LruCache<Pair<String, String>, ServiceDiscoveryResult> discoCache = new LruCache<>(20);
	private OnStatusChanged statusListener = new OnStatusChanged() {

		@Override
		public void onStatusChanged(final Account account) {
			XmppConnection connection = account.getXmppConnection();
			if (mOnAccountUpdate != null) {
				mOnAccountUpdate.onAccountUpdate();
			}
			if (account.getStatus() == Account.State.ONLINE) {
				synchronized (mLowPingTimeoutMode) {
					if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": leaving low ping timeout mode");
					}
				}
				if (account.setShowErrorNotification(true)) {
					databaseBackend.updateAccount(account);
				}
				mMessageArchiveService.executePendingQueries(account);
				if (connection != null && connection.getFeatures().csi()) {
					if (checkListeners()) {
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//inactive");
						connection.sendInactive();
					} else {
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//active");
						connection.sendActive();
					}
				}
				List<Conversation> conversations = getConversations();
				for (Conversation conversation : conversations) {
					if (conversation.getAccount() == account && !account.pendingConferenceJoins.contains(conversation)) {
						sendUnsentMessages(conversation);
					}
				}
				for (Conversation conversation : account.pendingConferenceLeaves) {
					leaveMuc(conversation);
				}
				account.pendingConferenceLeaves.clear();
				for (Conversation conversation : account.pendingConferenceJoins) {
					joinMuc(conversation);
				}
				account.pendingConferenceJoins.clear();
				scheduleWakeUpCall(Config.PING_MAX_INTERVAL, account.getUuid().hashCode());
			} else if (account.getStatus() == Account.State.OFFLINE || account.getStatus() == Account.State.DISABLED) {
				resetSendingToWaiting(account);
				if (account.isEnabled() && isInLowPingTimeoutMode(account)) {
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": went into offline state during low ping mode. reconnecting now");
					reconnectAccount(account, true, false);
				} else {
					int timeToReconnect = mRandom.nextInt(10) + 2;
					scheduleWakeUpCall(timeToReconnect, account.getUuid().hashCode());
				}
			} else if (account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL) {
				databaseBackend.updateAccount(account);
				reconnectAccount(account, true, false);
			} else if (account.getStatus() != Account.State.CONNECTING && account.getStatus() != Account.State.NO_INTERNET) {
				resetSendingToWaiting(account);
				if (connection != null && account.getStatus().isAttemptReconnect()) {
					final int next = connection.getTimeToNextAttempt();
					final boolean lowPingTimeoutMode = isInLowPingTimeoutMode(account);
					if (next <= 0) {
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error connecting account. reconnecting now. lowPingTimeout=" + Boolean.toString(lowPingTimeoutMode));
						reconnectAccount(account, true, false);
					} else {
						final int attempt = connection.getAttempt() + 1;
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error connecting account. try again in " + next + "s for the " + attempt + " time. lowPingTimeout=" + Boolean.toString(lowPingTimeoutMode));
						scheduleWakeUpCall(next, account.getUuid().hashCode());
					}
				}
			}
			getNotificationService().updateErrorNotification();
		}
	};
	private OpenPgpServiceConnection pgpServiceConnection;
	private PgpEngine mPgpEngine = null;
	private WakeLock wakeLock;
	private PowerManager pm;
	private LruCache<String, Bitmap> mBitmapCache;
	private EventReceiver mEventReceiver = new EventReceiver();

	private static String generateFetchKey(Account account, final Avatar avatar) {
		return account.getJid().asBareJid() + "_" + avatar.owner + "_" + avatar.sha1sum;
	}

	private boolean isInLowPingTimeoutMode(Account account) {
		synchronized (mLowPingTimeoutMode) {
			return mLowPingTimeoutMode.contains(account.getJid().asBareJid());
		}
	}

	public void startForcingForegroundNotification() {
		mForceForegroundService.set(true);
		toggleForegroundService();
	}

	public void stopForcingForegroundNotification() {
		mForceForegroundService.set(false);
		toggleForegroundService();
	}

	public boolean areMessagesInitialized() {
		return this.restoredFromDatabaseLatch.getCount() == 0;
	}

	public PgpEngine getPgpEngine() {
		if (!Config.supportOpenPgp()) {
			return null;
		} else if (pgpServiceConnection != null && pgpServiceConnection.isBound()) {
			if (this.mPgpEngine == null) {
				this.mPgpEngine = new PgpEngine(new OpenPgpApi(
						getApplicationContext(),
						pgpServiceConnection.getService()), this);
			}
			return mPgpEngine;
		} else {
			return null;
		}

	}

	public OpenPgpApi getOpenPgpApi() {
		if (!Config.supportOpenPgp()) {
			return null;
		} else if (pgpServiceConnection != null && pgpServiceConnection.isBound()) {
			return new OpenPgpApi(this, pgpServiceConnection.getService());
		} else {
			return null;
		}
	}

	public FileBackend getFileBackend() {
		return this.fileBackend;
	}

	public AvatarService getAvatarService() {
		return this.mAvatarService;
	}

	public void attachLocationToConversation(final Conversation conversation, final Uri uri, final UiCallback<Message> callback) {
		int encryption = conversation.getNextEncryption();
		if (encryption == Message.ENCRYPTION_PGP) {
			encryption = Message.ENCRYPTION_DECRYPTED;
		}
		Message message = new Message(conversation, uri.toString(), encryption);
		if (conversation.getNextCounterpart() != null) {
			message.setCounterpart(conversation.getNextCounterpart());
		}
		if (encryption == Message.ENCRYPTION_DECRYPTED) {
			getPgpEngine().encrypt(message, callback);
		} else {
			sendMessage(message);
			callback.success(message);
		}
	}

	public void attachFileToConversation(final Conversation conversation, final Uri uri, final String type, final UiCallback<Message> callback) {
		if (FileBackend.weOwnFile(this, uri)) {
			Log.d(Config.LOGTAG, "trying to attach file that belonged to us");
			callback.error(R.string.security_error_invalid_file_access, null);
			return;
		}
		final Message message;
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "", Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "", conversation.getNextEncryption());
		}
		message.setCounterpart(conversation.getNextCounterpart());
		message.setType(Message.TYPE_FILE);
		final AttachFileToConversationRunnable runnable = new AttachFileToConversationRunnable(this, uri, type, message, callback);
		if (runnable.isVideoMessage()) {
			mVideoCompressionExecutor.execute(runnable);
		} else {
			mFileAddingExecutor.execute(runnable);
		}
	}

	public void attachImageToConversation(final Conversation conversation, final Uri uri, final UiCallback<Message> callback) {
		if (FileBackend.weOwnFile(this, uri)) {
			Log.d(Config.LOGTAG, "trying to attach file that belonged to us");
			callback.error(R.string.security_error_invalid_file_access, null);
			return;
		}

		final String mimeType = MimeUtils.guessMimeTypeFromUri(this, uri);
		final String compressPictures = getCompressPicturesPreference();

		if ("never".equals(compressPictures)
				|| ("auto".equals(compressPictures) && getFileBackend().useImageAsIs(uri))
				|| (mimeType != null && mimeType.endsWith("/gif"))) {
			Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": not compressing picture. sending as file");
			attachFileToConversation(conversation, uri, mimeType, callback);
			return;
		}
		final Message message;
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "", Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "", conversation.getNextEncryption());
		}
		message.setCounterpart(conversation.getNextCounterpart());
		message.setType(Message.TYPE_IMAGE);
		mFileAddingExecutor.execute(() -> {
			try {
				getFileBackend().copyImageToPrivateStorage(message, uri);
				if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
					final PgpEngine pgpEngine = getPgpEngine();
					if (pgpEngine != null) {
						pgpEngine.encrypt(message, callback);
					} else if (callback != null) {
						callback.error(R.string.unable_to_connect_to_keychain, null);
					}
				} else {
					sendMessage(message);
					callback.success(message);
				}
			} catch (final FileBackend.FileCopyException e) {
				callback.error(e.getResId(), message);
			}
		});
	}

	public Conversation find(Bookmark bookmark) {
		return find(bookmark.getAccount(), bookmark.getJid());
	}

	public Conversation find(final Account account, final Jid jid) {
		return find(getConversations(), account, jid);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String action = intent == null ? null : intent.getAction();
		String pushedAccountHash = null;
		boolean interactive = false;
		if (action != null) {
			final String uuid = intent.getStringExtra("uuid");
			switch (action) {
				case ConnectivityManager.CONNECTIVITY_ACTION:
					if (hasInternetConnection() && Config.RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE) {
						resetAllAttemptCounts(true, false);
					}
					break;
				case ACTION_MERGE_PHONE_CONTACTS:
					if (restoredFromDatabaseLatch.getCount() == 0) {
						loadPhoneContacts();
					}
					return START_STICKY;
				case Intent.ACTION_SHUTDOWN:
					logoutAndSave(true);
					return START_NOT_STICKY;
				case ACTION_CLEAR_NOTIFICATION:
					mNotificationExecutor.execute(() -> {
						try {
							final Conversation c = findConversationByUuid(uuid);
							if (c != null) {
								mNotificationService.clear(c);
							} else {
								mNotificationService.clear();
							}
							restoredFromDatabaseLatch.await();

						} catch (InterruptedException e) {
							Log.d(Config.LOGTAG, "unable to process clear notification");
						}
					});
					break;
				case ACTION_DISMISS_ERROR_NOTIFICATIONS:
					dismissErrorNotifications();
					break;
				case ACTION_TRY_AGAIN:
					resetAllAttemptCounts(false, true);
					interactive = true;
					break;
				case ACTION_REPLY_TO_CONVERSATION:
					Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
					if (remoteInput == null) {
						break;
					}
					final CharSequence body = remoteInput.getCharSequence("text_reply");
					final boolean dismissNotification = intent.getBooleanExtra("dismiss_notification", false);
					if (body == null || body.length() <= 0) {
						break;
					}
					mNotificationExecutor.execute(() -> {
						try {
							restoredFromDatabaseLatch.await();
							final Conversation c = findConversationByUuid(uuid);
							if (c != null) {
								directReply(c, body.toString(), dismissNotification);
							}
						} catch (InterruptedException e) {
							Log.d(Config.LOGTAG, "unable to process direct reply");
						}
					});
					break;
				case ACTION_MARK_AS_READ:
					mNotificationExecutor.execute(() -> {
						final Conversation c = findConversationByUuid(uuid);
						if (c == null) {
							Log.d(Config.LOGTAG, "received mark read intent for unknown conversation (" + uuid + ")");
							return;
						}
						try {
							restoredFromDatabaseLatch.await();
							sendReadMarker(c, null);
						} catch (InterruptedException e) {
							Log.d(Config.LOGTAG, "unable to process notification read marker for conversation " + c.getName());
						}

					});
					break;
				case ACTION_SNOOZE:
					mNotificationExecutor.execute(() -> {
						final Conversation c = findConversationByUuid(uuid);
						if (c == null) {
							Log.d(Config.LOGTAG, "received snooze intent for unknown conversation (" + uuid + ")");
							return;
						}
						c.setMutedTill(System.currentTimeMillis() + 30 * 60 * 1000);
						mNotificationService.clear(c);
						updateConversation(c);
					});
				case AudioManager.RINGER_MODE_CHANGED_ACTION:
					if (dndOnSilentMode()) {
						refreshAllPresences();
					}
					break;
				case Intent.ACTION_SCREEN_ON:
					deactivateGracePeriod();
				case Intent.ACTION_SCREEN_OFF:
					if (awayWhenScreenOff()) {
						refreshAllPresences();
					}
					break;
				case ACTION_GCM_TOKEN_REFRESH:
					refreshAllGcmTokens();
					break;
				case ACTION_IDLE_PING:
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						scheduleNextIdlePing();
					}
					break;
				case ACTION_GCM_MESSAGE_RECEIVED:
					Log.d(Config.LOGTAG, "gcm push message arrived in service. extras=" + intent.getExtras());
					pushedAccountHash = intent.getStringExtra("account");
					break;
				case Intent.ACTION_SEND:
					Uri uri = intent.getData();
					if (uri != null) {
						Log.d(Config.LOGTAG, "received uri permission for " + uri.toString());
					}
					return START_STICKY;
			}
		}
		synchronized (this) {
			WakeLockHelper.acquire(wakeLock);
			boolean pingNow = ConnectivityManager.CONNECTIVITY_ACTION.equals(action);
			HashSet<Account> pingCandidates = new HashSet<>();
			for (Account account : accounts) {
				pingNow |= processAccountState(account,
						interactive,
						"ui".equals(action),
						CryptoHelper.getAccountFingerprint(account).equals(pushedAccountHash),
						pingCandidates);
			}
			if (pingNow) {
				for (Account account : pingCandidates) {
					final boolean lowTimeout = isInLowPingTimeoutMode(account);
					account.getXmppConnection().sendPing();
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + " send ping (action=" + action + ",lowTimeout=" + Boolean.toString(lowTimeout) + ")");
					scheduleWakeUpCall(lowTimeout ? Config.LOW_PING_TIMEOUT : Config.PING_TIMEOUT, account.getUuid().hashCode());
				}
			}
			WakeLockHelper.release(wakeLock);
		}
		if (SystemClock.elapsedRealtime() - mLastExpiryRun.get() >= Config.EXPIRY_INTERVAL) {
			expireOldMessages();
		}
		return START_STICKY;
	}

	private boolean processAccountState(Account account, boolean interactive, boolean isUiAction, boolean isAccountPushed, HashSet<Account> pingCandidates) {
		boolean pingNow = false;
		if (account.getStatus().isAttemptReconnect()) {
			if (!hasInternetConnection()) {
				account.setStatus(Account.State.NO_INTERNET);
				if (statusListener != null) {
					statusListener.onStatusChanged(account);
				}
			} else {
				if (account.getStatus() == Account.State.NO_INTERNET) {
					account.setStatus(Account.State.OFFLINE);
					if (statusListener != null) {
						statusListener.onStatusChanged(account);
					}
				}
				if (account.getStatus() == Account.State.ONLINE) {
					synchronized (mLowPingTimeoutMode) {
						long lastReceived = account.getXmppConnection().getLastPacketReceived();
						long lastSent = account.getXmppConnection().getLastPingSent();
						long pingInterval = isUiAction ? Config.PING_MIN_INTERVAL * 1000 : Config.PING_MAX_INTERVAL * 1000;
						long msToNextPing = (Math.max(lastReceived, lastSent) + pingInterval) - SystemClock.elapsedRealtime();
						int pingTimeout = mLowPingTimeoutMode.contains(account.getJid().asBareJid()) ? Config.LOW_PING_TIMEOUT * 1000 : Config.PING_TIMEOUT * 1000;
						long pingTimeoutIn = (lastSent + pingTimeout) - SystemClock.elapsedRealtime();
						if (lastSent > lastReceived) {
							if (pingTimeoutIn < 0) {
								Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ping timeout");
								this.reconnectAccount(account, true, interactive);
							} else {
								int secs = (int) (pingTimeoutIn / 1000);
								this.scheduleWakeUpCall(secs, account.getUuid().hashCode());
							}
						} else {
							pingCandidates.add(account);
							if (isAccountPushed) {
								pingNow = true;
								if (mLowPingTimeoutMode.add(account.getJid().asBareJid())) {
									Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": entering low ping timeout mode");
								}
							} else if (msToNextPing <= 0) {
								pingNow = true;
							} else {
								this.scheduleWakeUpCall((int) (msToNextPing / 1000), account.getUuid().hashCode());
								if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
									Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": leaving low ping timeout mode");
								}
							}
						}
					}
				} else if (account.getStatus() == Account.State.OFFLINE) {
					reconnectAccount(account, true, interactive);
				} else if (account.getStatus() == Account.State.CONNECTING) {
					long secondsSinceLastConnect = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastConnect()) / 1000;
					long secondsSinceLastDisco = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastDiscoStarted()) / 1000;
					long discoTimeout = Config.CONNECT_DISCO_TIMEOUT - secondsSinceLastDisco;
					long timeout = Config.CONNECT_TIMEOUT - secondsSinceLastConnect;
					if (timeout < 0) {
						Log.d(Config.LOGTAG, account.getJid() + ": time out during connect reconnecting (secondsSinceLast=" + secondsSinceLastConnect + ")");
						account.getXmppConnection().resetAttemptCount(false);
						reconnectAccount(account, true, interactive);
					} else if (discoTimeout < 0) {
						account.getXmppConnection().sendDiscoTimeout();
						scheduleWakeUpCall((int) Math.min(timeout, discoTimeout), account.getUuid().hashCode());
					} else {
						scheduleWakeUpCall((int) Math.min(timeout, discoTimeout), account.getUuid().hashCode());
					}
				} else {
					if (account.getXmppConnection().getTimeToNextAttempt() <= 0) {
						reconnectAccount(account, true, interactive);
					}
				}
			}
		}
		return pingNow;
	}

	public boolean isDataSaverDisabled() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
			return !connectivityManager.isActiveNetworkMetered()
					|| connectivityManager.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
		} else {
			return true;
		}
	}

	private void directReply(Conversation conversation, String body, final boolean dismissAfterReply) {
		Message message = new Message(conversation, body, conversation.getNextEncryption());
		message.markUnread();
		if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			getPgpEngine().encrypt(message, new UiCallback<Message>() {
				@Override
				public void success(Message message) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTED);
					sendMessage(message);
					if (dismissAfterReply) {
						markRead(message.getConversation(), true);
					} else {
						mNotificationService.pushFromDirectReply(message);
					}
				}

				@Override
				public void error(int errorCode, Message object) {

				}

				@Override
				public void userInputRequried(PendingIntent pi, Message object) {

				}
			});
		} else {
			sendMessage(message);
			if (dismissAfterReply) {
				markRead(conversation, true);
			} else {
				mNotificationService.pushFromDirectReply(message);
			}
		}
	}

	private boolean dndOnSilentMode() {
		return getBooleanPreference(SettingsActivity.DND_ON_SILENT_MODE, R.bool.dnd_on_silent_mode);
	}

	private boolean manuallyChangePresence() {
		return getBooleanPreference(SettingsActivity.MANUALLY_CHANGE_PRESENCE, R.bool.manually_change_presence);
	}

	private boolean treatVibrateAsSilent() {
		return getBooleanPreference(SettingsActivity.TREAT_VIBRATE_AS_SILENT, R.bool.treat_vibrate_as_silent);
	}

	private boolean awayWhenScreenOff() {
		return getBooleanPreference(SettingsActivity.AWAY_WHEN_SCREEN_IS_OFF, R.bool.away_when_screen_off);
	}

	private String getCompressPicturesPreference() {
		return getPreferences().getString("picture_compression", getResources().getString(R.string.picture_compression));
	}

	private Presence.Status getTargetPresence() {
		if (dndOnSilentMode() && isPhoneSilenced()) {
			return Presence.Status.DND;
		} else if (awayWhenScreenOff() && !isInteractive()) {
			return Presence.Status.AWAY;
		} else {
			return Presence.Status.ONLINE;
		}
	}

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public boolean isInteractive() {
		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

		final boolean isScreenOn;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			isScreenOn = pm.isScreenOn();
		} else {
			isScreenOn = pm.isInteractive();
		}
		return isScreenOn;
	}

	private boolean isPhoneSilenced() {
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		try {
			if (treatVibrateAsSilent()) {
				return audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
			} else {
				return audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
			}
		} catch (Throwable throwable) {
			Log.d(Config.LOGTAG, "platform bug in isPhoneSilenced (" + throwable.getMessage() + ")");
			return false;
		}
	}

	private void resetAllAttemptCounts(boolean reallyAll, boolean retryImmediately) {
		Log.d(Config.LOGTAG, "resetting all attempt counts");
		for (Account account : accounts) {
			if (account.hasErrorStatus() || reallyAll) {
				final XmppConnection connection = account.getXmppConnection();
				if (connection != null) {
					connection.resetAttemptCount(retryImmediately);
				}
			}
			if (account.setShowErrorNotification(true)) {
				databaseBackend.updateAccount(account);
			}
		}
		mNotificationService.updateErrorNotification();
	}

	private void dismissErrorNotifications() {
		for (final Account account : this.accounts) {
			if (account.hasErrorStatus()) {
				Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": dismissing error notification");
				if (account.setShowErrorNotification(false)) {
					databaseBackend.updateAccount(account);
				}
			}
		}
	}

	private void expireOldMessages() {
		expireOldMessages(false);
	}

	public void expireOldMessages(final boolean resetHasMessagesLeftOnServer) {
		mLastExpiryRun.set(SystemClock.elapsedRealtime());
		mDatabaseWriterExecutor.execute(() -> {
			long timestamp = getAutomaticMessageDeletionDate();
			if (timestamp > 0) {
				databaseBackend.expireOldMessages(timestamp);
				synchronized (XmppConnectionService.this.conversations) {
					for (Conversation conversation : XmppConnectionService.this.conversations) {
						conversation.expireOldMessages(timestamp);
						if (resetHasMessagesLeftOnServer) {
							conversation.messagesLoaded.set(true);
							conversation.setHasMessagesLeftOnServer(true);
						}
					}
				}
				updateConversationUi();
			}
		});
	}

	public boolean hasInternetConnection() {
		final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		try {
			final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			return activeNetwork != null && activeNetwork.isConnected();
		} catch (RuntimeException e) {
			Log.d(Config.LOGTAG, "unable to check for internet connection", e);
			return true; //if internet connection can not be checked it is probably best to just try
		}
	}

	@SuppressLint("TrulyRandom")
	@Override
	public void onCreate() {
		OmemoSetting.load(this);
		ExceptionHelper.init(getApplicationContext());
		PRNGFixes.apply();
		Resolver.init(this);
		this.mRandom = new SecureRandom();
		updateMemorizingTrustmanager();
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		final int cacheSize = maxMemory / 8;
		this.mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(final String key, final Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

		Log.d(Config.LOGTAG, "initializing database...");
		this.databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
		Log.d(Config.LOGTAG, "restoring accounts...");
		this.accounts = databaseBackend.getAccounts();
		final SharedPreferences.Editor editor = getPreferences().edit();
		if (this.accounts.size() == 0 && Arrays.asList("Sony", "Sony Ericsson").contains(Build.MANUFACTURER)) {
			editor.putBoolean(SettingsActivity.KEEP_FOREGROUND_SERVICE, true);
			Log.d(Config.LOGTAG, Build.MANUFACTURER + " is on blacklist. enabling foreground service");
		}
		editor.putBoolean(EventReceiver.SETTING_ENABLED_ACCOUNTS, hasEnabledAccounts()).apply();
		editor.apply();

		restoreFromDatabase();

		getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactObserver);
		new Thread(fileObserver::startWatching).start();
		if (Config.supportOpenPgp()) {
			this.pgpServiceConnection = new OpenPgpServiceConnection(this, "org.sufficientlysecure.keychain", new OpenPgpServiceConnection.OnBound() {
				@Override
				public void onBound(IOpenPgpService2 service) {
					for (Account account : accounts) {
						final PgpDecryptionService pgp = account.getPgpDecryptionService();
						if (pgp != null) {
							pgp.continueDecryption(true);
						}
					}
				}

				@Override
				public void onError(Exception e) {
				}
			});
			this.pgpServiceConnection.bindToService();
		}

		this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XmppConnectionService");

		toggleForegroundService();
		updateUnreadCountBadge();
		toggleScreenEventReceiver();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			scheduleNextIdlePing();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			registerReceiver(this.mEventReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		}
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		if (level >= TRIM_MEMORY_COMPLETE) {
			Log.d(Config.LOGTAG, "clear cache due to low memory");
			getBitmapCache().evictAll();
		}
	}

	@Override
	public void onDestroy() {
		try {
			unregisterReceiver(this.mEventReceiver);
		} catch (IllegalArgumentException e) {
			//ignored
		}
		fileObserver.stopWatching();
		super.onDestroy();
	}

	public void toggleScreenEventReceiver() {
		if (awayWhenScreenOff() && !manuallyChangePresence()) {
			final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			registerReceiver(this.mEventReceiver, filter);
		} else {
			try {
				unregisterReceiver(this.mEventReceiver);
			} catch (IllegalArgumentException e) {
				//ignored
			}
		}
	}

	public void toggleForegroundService() {
		if (mForceForegroundService.get() || (keepForegroundService() && hasEnabledAccounts())) {
			startForeground(NotificationService.FOREGROUND_NOTIFICATION_ID, this.mNotificationService.createForegroundNotification());
			Log.d(Config.LOGTAG, "started foreground service");
		} else {
			stopForeground(true);
			Log.d(Config.LOGTAG, "stopped foreground service");
		}
	}

	public boolean keepForegroundService() {
		return getBooleanPreference(SettingsActivity.KEEP_FOREGROUND_SERVICE, R.bool.enable_foreground_service);
	}

	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		if (keepForegroundService() || mForceForegroundService.get()) {
			Log.d(Config.LOGTAG, "ignoring onTaskRemoved because foreground service is activated");
		} else {
			this.logoutAndSave(false);
		}
	}

	private void logoutAndSave(boolean stop) {
		int activeAccounts = 0;
		for (final Account account : accounts) {
			if (account.getStatus() != Account.State.DISABLED) {
				databaseBackend.writeRoster(account.getRoster());
				activeAccounts++;
			}
			if (account.getXmppConnection() != null) {
				new Thread(() -> disconnect(account, false)).start();
			}
		}
		if (stop || activeAccounts == 0) {
			Log.d(Config.LOGTAG, "good bye");
			stopSelf();
		}
	}

	public void scheduleWakeUpCall(int seconds, int requestCode) {
		final long timeToWake = SystemClock.elapsedRealtime() + (seconds < 0 ? 1 : seconds + 1) * 1000;
		final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(this, EventReceiver.class);
		intent.setAction("ping");
		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, 0);
		try {
			alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
		} catch (RuntimeException e) {
			Log.e(Config.LOGTAG, "unable to schedule alarm for ping", e);
		}
	}

	@TargetApi(Build.VERSION_CODES.M)
	private void scheduleNextIdlePing() {
		final long timeToWake = SystemClock.elapsedRealtime() + (Config.IDLE_PING_INTERVAL * 1000);
		final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(this, EventReceiver.class);
		intent.setAction(ACTION_IDLE_PING);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
		try {
			alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
		} catch (RuntimeException e) {
			Log.d(Config.LOGTAG, "unable to schedule alarm for idle ping", e);
		}
	}

	public XmppConnection createConnection(final Account account) {
		final XmppConnection connection = new XmppConnection(account, this);
		connection.setOnMessagePacketReceivedListener(this.mMessageParser);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.mPresenceParser);
		connection.setOnUnregisteredIqPacketReceivedListener(this.mIqParser);
		connection.setOnJinglePacketReceivedListener(this.jingleListener);
		connection.setOnBindListener(this.mOnBindListener);
		connection.setOnMessageAcknowledgeListener(this.mOnMessageAcknowledgedListener);
		connection.addOnAdvancedStreamFeaturesAvailableListener(this.mMessageArchiveService);
		connection.addOnAdvancedStreamFeaturesAvailableListener(this.mAvatarService);
		AxolotlService axolotlService = account.getAxolotlService();
		if (axolotlService != null) {
			connection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
		}
		return connection;
	}

	public void sendChatState(Conversation conversation) {
		if (sendChatStates()) {
			MessagePacket packet = mMessageGenerator.generateChatState(conversation);
			sendMessagePacket(conversation.getAccount(), packet);
		}
	}

	private void sendFileMessage(final Message message, final boolean delay) {
		Log.d(Config.LOGTAG, "send file message");
		final Account account = message.getConversation().getAccount();
		if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
				|| message.getConversation().getMode() == Conversation.MODE_MULTI) {
			mHttpConnectionManager.createNewUploadConnection(message, delay);
		} else {
			mJingleConnectionManager.createNewConnection(message);
		}
	}

	public void sendMessage(final Message message) {
		sendMessage(message, false, false);
	}

	private void sendMessage(final Message message, final boolean resend, final boolean delay) {
		final Account account = message.getConversation().getAccount();
		if (account.setShowErrorNotification(true)) {
			databaseBackend.updateAccount(account);
			mNotificationService.updateErrorNotification();
		}
		final Conversation conversation = message.getConversation();
		account.deactivateGracePeriod();
		MessagePacket packet = null;
		final boolean addToConversation = (conversation.getMode() != Conversation.MODE_MULTI
				|| !Patches.BAD_MUC_REFLECTION.contains(account.getServerIdentity()))
				&& !message.edited();
		boolean saveInDb = addToConversation;
		message.setStatus(Message.STATUS_WAITING);

		if (account.isOnlineAndConnected()) {
			switch (message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					if (message.needsUploading()) {
						if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
								|| conversation.getMode() == Conversation.MODE_MULTI
								|| message.fixCounterpart()) {
							this.sendFileMessage(message, delay);
						} else {
							break;
						}
					} else {
						packet = mMessageGenerator.generateChat(message);
					}
					break;
				case Message.ENCRYPTION_PGP:
				case Message.ENCRYPTION_DECRYPTED:
					if (message.needsUploading()) {
						if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
								|| conversation.getMode() == Conversation.MODE_MULTI
								|| message.fixCounterpart()) {
							this.sendFileMessage(message, delay);
						} else {
							break;
						}
					} else {
						packet = mMessageGenerator.generatePgpChat(message);
					}
					break;
				case Message.ENCRYPTION_AXOLOTL:
					message.setFingerprint(account.getAxolotlService().getOwnFingerprint());
					if (message.needsUploading()) {
						if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
								|| conversation.getMode() == Conversation.MODE_MULTI
								|| message.fixCounterpart()) {
							this.sendFileMessage(message, delay);
						} else {
							break;
						}
					} else {
						XmppAxolotlMessage axolotlMessage = account.getAxolotlService().fetchAxolotlMessageFromCache(message);
						if (axolotlMessage == null) {
							account.getAxolotlService().preparePayloadMessage(message, delay);
						} else {
							packet = mMessageGenerator.generateAxolotlChat(message, axolotlMessage);
						}
					}
					break;

			}
			if (packet != null) {
				if (account.getXmppConnection().getFeatures().sm()
						|| (conversation.getMode() == Conversation.MODE_MULTI && message.getCounterpart().isBareJid())) {
					message.setStatus(Message.STATUS_UNSEND);
				} else {
					message.setStatus(Message.STATUS_SEND);
				}
			}
		} else {
			switch (message.getEncryption()) {
				case Message.ENCRYPTION_DECRYPTED:
					if (!message.needsUploading()) {
						String pgpBody = message.getEncryptedBody();
						String decryptedBody = message.getBody();
						message.setBody(pgpBody); //TODO might throw NPE
						message.setEncryption(Message.ENCRYPTION_PGP);
						if (message.edited()) {
							message.setBody(decryptedBody);
							message.setEncryption(Message.ENCRYPTION_DECRYPTED);
							databaseBackend.updateMessage(message, message.getEditedId());
							updateConversationUi();
							return;
						} else {
							databaseBackend.createMessage(message);
							saveInDb = false;
							message.setBody(decryptedBody);
							message.setEncryption(Message.ENCRYPTION_DECRYPTED);
						}
					}
					break;
				case Message.ENCRYPTION_AXOLOTL:
					message.setFingerprint(account.getAxolotlService().getOwnFingerprint());
					break;
			}
		}


		boolean mucMessage = conversation.getMode() == Conversation.MODE_MULTI && message.getType() != Message.TYPE_PRIVATE;
		if (mucMessage) {
			message.setCounterpart(conversation.getMucOptions().getSelf().getFullJid());
		}

		if (resend) {
			if (packet != null && addToConversation) {
				if (account.getXmppConnection().getFeatures().sm() || mucMessage) {
					markMessage(message, Message.STATUS_UNSEND);
				} else {
					markMessage(message, Message.STATUS_SEND);
				}
			}
		} else {
			if (addToConversation) {
				conversation.add(message);
			}
			if (saveInDb) {
				databaseBackend.createMessage(message);
			} else if (message.edited()) {
				databaseBackend.updateMessage(message, message.getEditedId());
			}
			updateConversationUi();
		}
		if (packet != null) {
			if (delay) {
				mMessageGenerator.addDelay(packet, message.getTimeSent());
			}
			if (conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
				if (this.sendChatStates()) {
					packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
				}
			}
			sendMessagePacket(account, packet);
		}
	}

	private void sendUnsentMessages(final Conversation conversation) {
		conversation.findWaitingMessages(new Conversation.OnMessageFound() {

			@Override
			public void onMessageFound(Message message) {
				resendMessage(message, true);
			}
		});
	}

	public void resendMessage(final Message message, final boolean delay) {
		sendMessage(message, true, delay);
	}

	public void fetchRosterFromServer(final Account account) {
		final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
		if (!"".equals(account.getRosterVersion())) {
			Log.d(Config.LOGTAG, account.getJid().asBareJid()
					+ ": fetching roster version " + account.getRosterVersion());
		} else {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": fetching roster");
		}
		iqPacket.query(Namespace.ROSTER).setAttribute("ver", account.getRosterVersion());
		sendIqPacket(account, iqPacket, mIqParser);
	}

	public void fetchBookmarks(final Account account) {
		final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
		final Element query = iqPacket.query("jabber:iq:private");
		query.addChild("storage", "storage:bookmarks");
		final OnIqPacketReceived callback = new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					final Element query = packet.query();
					final HashMap<Jid, Bookmark> bookmarks = new HashMap<>();
					final Element storage = query.findChild("storage", "storage:bookmarks");
					final boolean autojoin = respectAutojoin();
					if (storage != null) {
						for (final Element item : storage.getChildren()) {
							if (item.getName().equals("conference")) {
								final Bookmark bookmark = Bookmark.parse(item, account);
								Bookmark old = bookmarks.put(bookmark.getJid(), bookmark);
								if (old != null && old.getBookmarkName() != null && bookmark.getBookmarkName() == null) {
									bookmark.setBookmarkName(old.getBookmarkName());
								}
								Conversation conversation = find(bookmark);
								if (conversation != null) {
									bookmark.setConversation(conversation);
								} else if (bookmark.autojoin() && bookmark.getJid() != null && autojoin) {
									conversation = findOrCreateConversation(account, bookmark.getJid(), true, true, false);
									bookmark.setConversation(conversation);
								}
							}
						}
					}
					account.setBookmarks(new CopyOnWriteArrayList<>(bookmarks.values()));
				} else {
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": could not fetch bookmarks");
				}
			}
		};
		sendIqPacket(account, iqPacket, callback);
	}

	public void pushBookmarks(Account account) {
		Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": pushing bookmarks");
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE.SET);
		Element query = iqPacket.query("jabber:iq:private");
		Element storage = query.addChild("storage", "storage:bookmarks");
		for (Bookmark bookmark : account.getBookmarks()) {
			storage.addChild(bookmark);
		}
		sendIqPacket(account, iqPacket, mDefaultIqHandler);
	}

	private void restoreFromDatabase() {
		synchronized (this.conversations) {
			final Map<String, Account> accountLookupTable = new Hashtable<>();
			for (Account account : this.accounts) {
				accountLookupTable.put(account.getUuid(), account);
			}
			Log.d(Config.LOGTAG, "restoring conversations...");
			final long startTimeConversationsRestore = SystemClock.elapsedRealtime();
			this.conversations.addAll(databaseBackend.getConversations(Conversation.STATUS_AVAILABLE));
			for (Iterator<Conversation> iterator = conversations.listIterator(); iterator.hasNext(); ) {
				Conversation conversation = iterator.next();
				Account account = accountLookupTable.get(conversation.getAccountUuid());
				if (account != null) {
					conversation.setAccount(account);
				} else {
					Log.e(Config.LOGTAG, "unable to restore Conversations with " + conversation.getJid());
					iterator.remove();
				}
			}
			long diffConversationsRestore = SystemClock.elapsedRealtime() - startTimeConversationsRestore;
			Log.d(Config.LOGTAG, "finished restoring conversations in " + diffConversationsRestore + "ms");
			Runnable runnable = () -> {
				long deletionDate = getAutomaticMessageDeletionDate();
				mLastExpiryRun.set(SystemClock.elapsedRealtime());
				if (deletionDate > 0) {
					Log.d(Config.LOGTAG, "deleting messages that are older than " + AbstractGenerator.getTimestamp(deletionDate));
					databaseBackend.expireOldMessages(deletionDate);
				}
				Log.d(Config.LOGTAG, "restoring roster...");
				for (Account account : accounts) {
					databaseBackend.readRoster(account.getRoster());
					account.initAccountServices(XmppConnectionService.this); //roster needs to be loaded at this stage
				}
				getBitmapCache().evictAll();
				loadPhoneContacts();
				Log.d(Config.LOGTAG, "restoring messages...");
				final long startMessageRestore = SystemClock.elapsedRealtime();
				final Conversation quickLoad = QuickLoader.get(this.conversations);
				if (quickLoad != null) {
					restoreMessages(quickLoad);
					updateConversationUi();
					final long diffMessageRestore = SystemClock.elapsedRealtime() - startMessageRestore;
					Log.d(Config.LOGTAG,"quickly restored "+quickLoad.getName()+" after " + diffMessageRestore + "ms");
				}
				for (Conversation conversation : this.conversations) {
					if (quickLoad != conversation) {
						restoreMessages(conversation);
					}
				}
				mNotificationService.finishBacklog(false);
				restoredFromDatabaseLatch.countDown();
				final long diffMessageRestore = SystemClock.elapsedRealtime() - startMessageRestore;
				Log.d(Config.LOGTAG, "finished restoring messages in " + diffMessageRestore + "ms");
				updateConversationUi();
			};
			mDatabaseReaderExecutor.execute(runnable); //will contain one write command (expiry) but that's fine
		}
	}

	private void restoreMessages(Conversation conversation) {
		conversation.addAll(0, databaseBackend.getMessages(conversation, Config.PAGE_SIZE));
		checkDeletedFiles(conversation);
		conversation.findUnsentTextMessages(message -> markMessage(message, Message.STATUS_WAITING));
		conversation.findUnreadMessages(message -> mNotificationService.pushFromBacklog(message));
	}

	public void loadPhoneContacts() {
		mContactMergerExecutor.execute(() -> PhoneHelper.loadPhoneContacts(XmppConnectionService.this, new OnPhoneContactsLoadedListener() {
			@Override
			public void onPhoneContactsLoaded(List<Bundle> phoneContacts) {
				Log.d(Config.LOGTAG, "start merging phone contacts with roster");
				for (Account account : accounts) {
					List<Contact> withSystemAccounts = account.getRoster().getWithSystemAccounts();
					for (Bundle phoneContact : phoneContacts) {
						Jid jid;
						try {
							jid = Jid.of(phoneContact.getString("jid"));
						} catch (final IllegalArgumentException e) {
							continue;
						}
						final Contact contact = account.getRoster().getContact(jid);
						String systemAccount = phoneContact.getInt("phoneid")
								+ "#"
								+ phoneContact.getString("lookup");
						contact.setSystemAccount(systemAccount);
						boolean needsCacheClean = contact.setPhotoUri(phoneContact.getString("photouri"));
						needsCacheClean |= contact.setSystemName(phoneContact.getString("displayname"));
						if (needsCacheClean) {
							getAvatarService().clear(contact);
						}
						withSystemAccounts.remove(contact);
					}
					for (Contact contact : withSystemAccounts) {
						contact.setSystemAccount(null);
						boolean needsCacheClean = contact.setPhotoUri(null);
						needsCacheClean |= contact.setSystemName(null);
						if (needsCacheClean) {
							getAvatarService().clear(contact);
						}
					}
				}
				Log.d(Config.LOGTAG, "finished merging phone contacts");
				mShortcutService.refresh(mInitialAddressbookSyncCompleted.compareAndSet(false, true));
				updateAccountUi();
			}
		}));
	}


	public void syncRoster(final Account account) {
		mRosterSyncTaskManager.execute(account, () -> databaseBackend.writeRoster(account.getRoster()));
	}

	public List<Conversation> getConversations() {
		return this.conversations;
	}

	private void checkDeletedFiles(Conversation conversation) {
		conversation.findMessagesWithFiles(message -> {
			if (!getFileBackend().isFileAvailable(message)) {
				message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
				final int s = message.getStatus();
				if (s == Message.STATUS_WAITING || s == Message.STATUS_OFFERED || s == Message.STATUS_UNSEND) {
					markMessage(message, Message.STATUS_SEND_FAILED);
				}
			}
		});
	}

	private void markFileDeleted(final String path) {
		Log.d(Config.LOGTAG, "deleted file " + path);
		for (Conversation conversation : getConversations()) {
			conversation.findMessagesWithFiles(message -> {
				DownloadableFile file = fileBackend.getFile(message);
				if (file.getAbsolutePath().equals(path)) {
					if (!file.exists()) {
						message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
						final int s = message.getStatus();
						if (s == Message.STATUS_WAITING || s == Message.STATUS_OFFERED || s == Message.STATUS_UNSEND) {
							markMessage(message, Message.STATUS_SEND_FAILED);
						} else {
							updateConversationUi();
						}
					} else {
						Log.d(Config.LOGTAG, "found matching message for file " + path + " but file still exists");
					}
				}
			});
		}
	}

	public void populateWithOrderedConversations(final List<Conversation> list) {
		populateWithOrderedConversations(list, true);
	}

	public void populateWithOrderedConversations(final List<Conversation> list, boolean includeNoFileUpload) {
		list.clear();
		if (includeNoFileUpload) {
			list.addAll(getConversations());
		} else {
			for (Conversation conversation : getConversations()) {
				if (conversation.getMode() == Conversation.MODE_SINGLE
						|| conversation.getAccount().httpUploadAvailable()) {
					list.add(conversation);
				}
			}
		}
		try {
			Collections.sort(list);
		} catch (IllegalArgumentException e) {
			//ignore
		}
	}

	public void loadMoreMessages(final Conversation conversation, final long timestamp, final OnMoreMessagesLoaded callback) {
		if (XmppConnectionService.this.getMessageArchiveService().queryInProgress(conversation, callback)) {
			return;
		} else if (timestamp == 0) {
			return;
		}
		Log.d(Config.LOGTAG, "load more messages for " + conversation.getName() + " prior to " + MessageGenerator.getTimestamp(timestamp));
		final Runnable runnable = () -> {
			final Account account = conversation.getAccount();
			List<Message> messages = databaseBackend.getMessages(conversation, 50, timestamp);
			if (messages.size() > 0) {
				conversation.addAll(0, messages);
				checkDeletedFiles(conversation);
				callback.onMoreMessagesLoaded(messages.size(), conversation);
			} else if (conversation.hasMessagesLeftOnServer()
					&& account.isOnlineAndConnected()
					&& conversation.getLastClearHistory().getTimestamp() == 0) {
				final boolean mamAvailable;
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					mamAvailable = account.getXmppConnection().getFeatures().mam() && !conversation.getContact().isBlocked();
				} else {
					mamAvailable = conversation.getMucOptions().mamSupport();
				}
				if (mamAvailable) {
					MessageArchiveService.Query query = getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
					if (query != null) {
						query.setCallback(callback);
						callback.informUser(R.string.fetching_history_from_server);
					} else {
						callback.informUser(R.string.not_fetching_history_retention_period);
					}

				}
			}
		};
		mDatabaseReaderExecutor.execute(runnable);
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public List<Conversation> findAllConferencesWith(Contact contact) {
		ArrayList<Conversation> results = new ArrayList<>();
		for (final Conversation c : conversations) {
			if (c.getMode() == Conversation.MODE_MULTI
					&& (c.getJid().asBareJid().equals(c.getJid().asBareJid()) || c.getMucOptions().isContactInRoom(contact))) {
				results.add(c);
			}
		}
		return results;
	}

	public Conversation find(final Iterable<Conversation> haystack, final Contact contact) {
		for (final Conversation conversation : haystack) {
			if (conversation.getContact() == contact) {
				return conversation;
			}
		}
		return null;
	}

	public Conversation find(final Iterable<Conversation> haystack, final Account account, final Jid jid) {
		if (jid == null) {
			return null;
		}
		for (final Conversation conversation : haystack) {
			if ((account == null || conversation.getAccount() == account)
					&& (conversation.getJid().asBareJid().equals(jid.asBareJid()))) {
				return conversation;
			}
		}
		return null;
	}

	public boolean isConversationsListEmpty(final Conversation ignore) {
		synchronized (this.conversations) {
			final int size = this.conversations.size();
			return size == 0 || size == 1 && this.conversations.get(0) == ignore;
		}
	}

	public boolean isConversationStillOpen(final Conversation conversation) {
		synchronized (this.conversations) {
			for (Conversation current : this.conversations) {
				if (current == conversation) {
					return true;
				}
			}
		}
		return false;
	}

	public Conversation findOrCreateConversation(Account account, Jid jid, boolean muc, final boolean async) {
		return this.findOrCreateConversation(account, jid, muc, false, async);
	}

	public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final boolean async) {
		return this.findOrCreateConversation(account, jid, muc, joinAfterCreate, null, async);
	}

	public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final MessageArchiveService.Query query, final boolean async) {
		synchronized (this.conversations) {
			Conversation conversation = find(account, jid);
			if (conversation != null) {
				return conversation;
			}
			conversation = databaseBackend.findConversation(account, jid);
			final boolean loadMessagesFromDb;
			if (conversation != null) {
				conversation.setStatus(Conversation.STATUS_AVAILABLE);
				conversation.setAccount(account);
				if (muc) {
					conversation.setMode(Conversation.MODE_MULTI);
					conversation.setContactJid(jid);
				} else {
					conversation.setMode(Conversation.MODE_SINGLE);
					conversation.setContactJid(jid.asBareJid());
				}
				databaseBackend.updateConversation(conversation);
				loadMessagesFromDb = conversation.messagesLoaded.compareAndSet(true, false);
			} else {
				String conversationName;
				Contact contact = account.getRoster().getContact(jid);
				if (contact != null) {
					conversationName = contact.getDisplayName();
				} else {
					conversationName = jid.getLocal();
				}
				if (muc) {
					conversation = new Conversation(conversationName, account, jid,
							Conversation.MODE_MULTI);
				} else {
					conversation = new Conversation(conversationName, account, jid.asBareJid(),
							Conversation.MODE_SINGLE);
				}
				this.databaseBackend.createConversation(conversation);
				loadMessagesFromDb = false;
			}
			final Conversation c = conversation;
			final Runnable runnable = () -> {
				if (loadMessagesFromDb) {
					c.addAll(0, databaseBackend.getMessages(c, Config.PAGE_SIZE));
					updateConversationUi();
					c.messagesLoaded.set(true);
				}
				if (account.getXmppConnection() != null
						&& !c.getContact().isBlocked()
						&& account.getXmppConnection().getFeatures().mam()
						&& !muc) {
					if (query == null) {
						mMessageArchiveService.query(c);
					} else {
						if (query.getConversation() == null) {
							mMessageArchiveService.query(c, query.getStart(), query.isCatchup());
						}
					}
				}
				checkDeletedFiles(c);
				if (joinAfterCreate) {
					joinMuc(c);
				}
			};
			if (async) {
				mDatabaseReaderExecutor.execute(runnable);
			} else {
				runnable.run();
			}
			this.conversations.add(conversation);
			updateConversationUi();
			return conversation;
		}
	}

	public void archiveConversation(Conversation conversation) {
		getNotificationService().clear(conversation);
		conversation.setStatus(Conversation.STATUS_ARCHIVED);
		conversation.setNextMessage(null);
		synchronized (this.conversations) {
			getMessageArchiveService().kill(conversation);
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
					Bookmark bookmark = conversation.getBookmark();
					if (bookmark != null && bookmark.autojoin() && respectAutojoin()) {
						bookmark.setAutojoin(false);
						pushBookmarks(bookmark.getAccount());
					}
				}
				leaveMuc(conversation);
			} else {
				if (conversation.getContact().getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
					Log.d(Config.LOGTAG, "Canceling presence request from " + conversation.getJid().toString());
					sendPresencePacket(
							conversation.getAccount(),
							mPresenceGenerator.stopPresenceUpdatesTo(conversation.getContact())
					);
				}
			}
			updateConversation(conversation);
			this.conversations.remove(conversation);
			updateConversationUi();
		}
	}

	public void createAccount(final Account account) {
		account.initAccountServices(this);
		databaseBackend.createAccount(account);
		this.accounts.add(account);
		this.reconnectAccountInBackground(account);
		updateAccountUi();
		syncEnabledAccountSetting();
		toggleForegroundService();
	}

	private void syncEnabledAccountSetting() {
		getPreferences().edit().putBoolean(EventReceiver.SETTING_ENABLED_ACCOUNTS, hasEnabledAccounts()).apply();
	}

	public void createAccountFromKey(final String alias, final OnAccountCreated callback) {
		new Thread(() -> {
			try {
				final X509Certificate[] chain = KeyChain.getCertificateChain(this, alias);
				final X509Certificate cert = chain != null && chain.length > 0 ? chain[0] : null;
				if (cert == null) {
					callback.informUser(R.string.unable_to_parse_certificate);
					return;
				}
				Pair<Jid, String> info = CryptoHelper.extractJidAndName(cert);
				if (info == null) {
					callback.informUser(R.string.certificate_does_not_contain_jid);
					return;
				}
				if (findAccountByJid(info.first) == null) {
					Account account = new Account(info.first, "");
					account.setPrivateKeyAlias(alias);
					account.setOption(Account.OPTION_DISABLED, true);
					account.setDisplayName(info.second);
					createAccount(account);
					callback.onAccountCreated(account);
					if (Config.X509_VERIFICATION) {
						try {
							getMemorizingTrustManager().getNonInteractive(account.getJid().getDomain()).checkClientTrusted(chain, "RSA");
						} catch (CertificateException e) {
							callback.informUser(R.string.certificate_chain_is_not_trusted);
						}
					}
				} else {
					callback.informUser(R.string.account_already_exists);
				}
			} catch (Exception e) {
				e.printStackTrace();
				callback.informUser(R.string.unable_to_parse_certificate);
			}
		}).start();

	}

	public void updateKeyInAccount(final Account account, final String alias) {
		Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": update key in account " + alias);
		try {
			X509Certificate[] chain = KeyChain.getCertificateChain(XmppConnectionService.this, alias);
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + " loaded certificate chain");
			Pair<Jid, String> info = CryptoHelper.extractJidAndName(chain[0]);
			if (info == null) {
				showErrorToastInUi(R.string.certificate_does_not_contain_jid);
				return;
			}
			if (account.getJid().asBareJid().equals(info.first)) {
				account.setPrivateKeyAlias(alias);
				account.setDisplayName(info.second);
				databaseBackend.updateAccount(account);
				if (Config.X509_VERIFICATION) {
					try {
						getMemorizingTrustManager().getNonInteractive().checkClientTrusted(chain, "RSA");
					} catch (CertificateException e) {
						showErrorToastInUi(R.string.certificate_chain_is_not_trusted);
					}
					account.getAxolotlService().regenerateKeys(true);
				}
			} else {
				showErrorToastInUi(R.string.jid_does_not_match_certificate);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean updateAccount(final Account account) {
		if (databaseBackend.updateAccount(account)) {
			account.setShowErrorNotification(true);
			this.statusListener.onStatusChanged(account);
			databaseBackend.updateAccount(account);
			reconnectAccountInBackground(account);
			updateAccountUi();
			getNotificationService().updateErrorNotification();
			toggleForegroundService();
			syncEnabledAccountSetting();
			return true;
		} else {
			return false;
		}
	}

	public void updateAccountPasswordOnServer(final Account account, final String newPassword, final OnAccountPasswordChanged callback) {
		final IqPacket iq = getIqGenerator().generateSetPassword(account, newPassword);
		sendIqPacket(account, iq, (a, packet) -> {
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				a.setPassword(newPassword);
				a.setOption(Account.OPTION_MAGIC_CREATE, false);
				databaseBackend.updateAccount(a);
				callback.onPasswordChangeSucceeded();
			} else {
				callback.onPasswordChangeFailed();
			}
		});
	}

	public void deleteAccount(final Account account) {
		synchronized (this.conversations) {
			for (final Conversation conversation : conversations) {
				if (conversation.getAccount() == account) {
					if (conversation.getMode() == Conversation.MODE_MULTI) {
						leaveMuc(conversation);
					}
					conversations.remove(conversation);
				}
			}
			if (account.getXmppConnection() != null) {
				new Thread(() -> disconnect(account, true)).start();
			}
			final Runnable runnable = () -> {
				if (!databaseBackend.deleteAccount(account)) {
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to delete account");
				}
			};
			mDatabaseWriterExecutor.execute(runnable);
			this.accounts.remove(account);
			this.mRosterSyncTaskManager.clear(account);
			updateAccountUi();
			getNotificationService().updateErrorNotification();
			syncEnabledAccountSetting();
		}
	}

	public void setOnConversationListChangedListener(OnConversationUpdate listener) {
		synchronized (this) {
			this.mLastActivity = System.currentTimeMillis();
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnConversationUpdate = listener;
			this.mNotificationService.setIsInForeground(true);
			if (this.convChangedListenerCount < 2) {
				this.convChangedListenerCount++;
			}
		}
	}

	public void removeOnConversationListChangedListener() {
		synchronized (this) {
			this.convChangedListenerCount--;
			if (this.convChangedListenerCount <= 0) {
				this.convChangedListenerCount = 0;
				this.mOnConversationUpdate = null;
				this.mNotificationService.setIsInForeground(false);
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnShowErrorToastListener(OnShowErrorToast onShowErrorToast) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnShowErrorToast = onShowErrorToast;
			if (this.showErrorToastListenerCount < 2) {
				this.showErrorToastListenerCount++;
			}
		}
		this.mOnShowErrorToast = onShowErrorToast;
	}

	public void removeOnShowErrorToastListener() {
		synchronized (this) {
			this.showErrorToastListenerCount--;
			if (this.showErrorToastListenerCount <= 0) {
				this.showErrorToastListenerCount = 0;
				this.mOnShowErrorToast = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnAccountListChangedListener(OnAccountUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnAccountUpdate = listener;
			if (this.accountChangedListenerCount < 2) {
				this.accountChangedListenerCount++;
			}
		}
	}

	public void removeOnAccountListChangedListener() {
		synchronized (this) {
			this.accountChangedListenerCount--;
			if (this.accountChangedListenerCount <= 0) {
				this.mOnAccountUpdate = null;
				this.accountChangedListenerCount = 0;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnCaptchaRequestedListener(OnCaptchaRequested listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnCaptchaRequested = listener;
			if (this.captchaRequestedListenerCount < 2) {
				this.captchaRequestedListenerCount++;
			}
		}
	}

	public void removeOnCaptchaRequestedListener() {
		synchronized (this) {
			this.captchaRequestedListenerCount--;
			if (this.captchaRequestedListenerCount <= 0) {
				this.mOnCaptchaRequested = null;
				this.captchaRequestedListenerCount = 0;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnRosterUpdateListener(final OnRosterUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnRosterUpdate = listener;
			if (this.rosterChangedListenerCount < 2) {
				this.rosterChangedListenerCount++;
			}
		}
	}

	public void removeOnRosterUpdateListener() {
		synchronized (this) {
			this.rosterChangedListenerCount--;
			if (this.rosterChangedListenerCount <= 0) {
				this.rosterChangedListenerCount = 0;
				this.mOnRosterUpdate = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnUpdateBlocklistListener(final OnUpdateBlocklist listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnUpdateBlocklist = listener;
			if (this.updateBlocklistListenerCount < 2) {
				this.updateBlocklistListenerCount++;
			}
		}
	}

	public void removeOnUpdateBlocklistListener() {
		synchronized (this) {
			this.updateBlocklistListenerCount--;
			if (this.updateBlocklistListenerCount <= 0) {
				this.updateBlocklistListenerCount = 0;
				this.mOnUpdateBlocklist = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnKeyStatusUpdatedListener(final OnKeyStatusUpdated listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnKeyStatusUpdated = listener;
			if (this.keyStatusUpdatedListenerCount < 2) {
				this.keyStatusUpdatedListenerCount++;
			}
		}
	}

	public void removeOnNewKeysAvailableListener() {
		synchronized (this) {
			this.keyStatusUpdatedListenerCount--;
			if (this.keyStatusUpdatedListenerCount <= 0) {
				this.keyStatusUpdatedListenerCount = 0;
				this.mOnKeyStatusUpdated = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnMucRosterUpdateListener(OnMucRosterUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnMucRosterUpdate = listener;
			if (this.mucRosterChangedListenerCount < 2) {
				this.mucRosterChangedListenerCount++;
			}
		}
	}

	public void removeOnMucRosterUpdateListener() {
		synchronized (this) {
			this.mucRosterChangedListenerCount--;
			if (this.mucRosterChangedListenerCount <= 0) {
				this.mucRosterChangedListenerCount = 0;
				this.mOnMucRosterUpdate = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public boolean checkListeners() {
		return (this.mOnAccountUpdate == null
				&& this.mOnConversationUpdate == null
				&& this.mOnRosterUpdate == null
				&& this.mOnCaptchaRequested == null
				&& this.mOnUpdateBlocklist == null
				&& this.mOnShowErrorToast == null
				&& this.mOnKeyStatusUpdated == null);
	}

	private void switchToForeground() {
		final boolean broadcastLastActivity = broadcastLastActivity();
		for (Conversation conversation : getConversations()) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				conversation.getMucOptions().resetChatState();
			} else {
				conversation.setIncomingChatState(Config.DEFAULT_CHATSTATE);
			}
		}
		for (Account account : getAccounts()) {
			if (account.getStatus() == Account.State.ONLINE) {
				account.deactivateGracePeriod();
				final XmppConnection connection = account.getXmppConnection();
				if (connection != null) {
					if (connection.getFeatures().csi()) {
						connection.sendActive();
					}
					if (broadcastLastActivity) {
						sendPresence(account, false); //send new presence but don't include idle because we are not
					}
				}
			}
		}
		Log.d(Config.LOGTAG, "app switched into foreground");
	}

	private void switchToBackground() {
		final boolean broadcastLastActivity = broadcastLastActivity();
		for (Account account : getAccounts()) {
			if (account.getStatus() == Account.State.ONLINE) {
				XmppConnection connection = account.getXmppConnection();
				if (connection != null) {
					if (broadcastLastActivity) {
						sendPresence(account, true);
					}
					if (connection.getFeatures().csi()) {
						connection.sendInactive();
					}
				}
			}
		}
		this.mNotificationService.setIsInForeground(false);
		Log.d(Config.LOGTAG, "app switched into background");
	}

	private void connectMultiModeConversations(Account account) {
		List<Conversation> conversations = getConversations();
		for (Conversation conversation : conversations) {
			if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getAccount() == account) {
				joinMuc(conversation);
			}
		}
	}

	public void joinMuc(Conversation conversation) {
		joinMuc(conversation, null, false);
	}

	public void joinMuc(Conversation conversation, boolean followedInvite) {
		joinMuc(conversation, null, followedInvite);
	}

	private void joinMuc(Conversation conversation, final OnConferenceJoined onConferenceJoined) {
		joinMuc(conversation, onConferenceJoined, false);
	}

	private void joinMuc(Conversation conversation, final OnConferenceJoined onConferenceJoined, final boolean followedInvite) {
		Account account = conversation.getAccount();
		account.pendingConferenceJoins.remove(conversation);
		account.pendingConferenceLeaves.remove(conversation);
		if (account.getStatus() == Account.State.ONLINE) {
			sendPresencePacket(account, mPresenceGenerator.leave(conversation.getMucOptions()));
			conversation.resetMucOptions();
			if (onConferenceJoined != null) {
				conversation.getMucOptions().flagNoAutoPushConfiguration();
			}
			conversation.setHasMessagesLeftOnServer(false);
			fetchConferenceConfiguration(conversation, new OnConferenceConfigurationFetched() {

				private void join(Conversation conversation) {
					Account account = conversation.getAccount();
					final MucOptions mucOptions = conversation.getMucOptions();
					final Jid joinJid = mucOptions.getSelf().getFullJid();
					Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": joining conversation " + joinJid.toString());
					PresencePacket packet = mPresenceGenerator.selfPresence(account, Presence.Status.ONLINE, mucOptions.nonanonymous() || onConferenceJoined != null);
					packet.setTo(joinJid);
					Element x = packet.addChild("x", "http://jabber.org/protocol/muc");
					if (conversation.getMucOptions().getPassword() != null) {
						x.addChild("password").setContent(mucOptions.getPassword());
					}

					if (mucOptions.mamSupport()) {
						// Use MAM instead of the limited muc history to get history
						x.addChild("history").setAttribute("maxchars", "0");
					} else {
						// Fallback to muc history
						x.addChild("history").setAttribute("since", PresenceGenerator.getTimestamp(conversation.getLastMessageTransmitted().getTimestamp()));
					}
					sendPresencePacket(account, packet);
					if (onConferenceJoined != null) {
						onConferenceJoined.onConferenceJoined(conversation);
					}
					if (!joinJid.equals(conversation.getJid())) {
						conversation.setContactJid(joinJid);
						databaseBackend.updateConversation(conversation);
					}

					if (mucOptions.mamSupport()) {
						getMessageArchiveService().catchupMUC(conversation);
					}
					if (mucOptions.isPrivateAndNonAnonymous()) {
						fetchConferenceMembers(conversation);
						if (followedInvite && conversation.getBookmark() == null) {
							saveConversationAsBookmark(conversation, null);
						}
					}
					sendUnsentMessages(conversation);
				}

				@Override
				public void onConferenceConfigurationFetched(Conversation conversation) {
					join(conversation);
				}

				@Override
				public void onFetchFailed(final Conversation conversation, Element error) {
					if (error != null && "remote-server-not-found".equals(error.getName())) {
						conversation.getMucOptions().setError(MucOptions.Error.SERVER_NOT_FOUND);
						updateConversationUi();
					} else {
						join(conversation);
						fetchConferenceConfiguration(conversation);
					}
				}
			});
			updateConversationUi();
		} else {
			account.pendingConferenceJoins.add(conversation);
			conversation.resetMucOptions();
			conversation.setHasMessagesLeftOnServer(false);
			updateConversationUi();
		}
	}

	private void fetchConferenceMembers(final Conversation conversation) {
		final Account account = conversation.getAccount();
		final AxolotlService axolotlService = account.getAxolotlService();
		final String[] affiliations = {"member", "admin", "owner"};
		OnIqPacketReceived callback = new OnIqPacketReceived() {

			private int i = 0;
			private boolean success = true;

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {

				Element query = packet.query("http://jabber.org/protocol/muc#admin");
				if (packet.getType() == IqPacket.TYPE.RESULT && query != null) {
					for (Element child : query.getChildren()) {
						if ("item".equals(child.getName())) {
							MucOptions.User user = AbstractParser.parseItem(conversation, child);
							if (!user.realJidMatchesAccount()) {
								boolean isNew = conversation.getMucOptions().updateUser(user);
								Contact contact = user.getContact();
								if (isNew
										&& user.getRealJid() != null
										&& (contact == null || !contact.mutualPresenceSubscription())
										&& axolotlService.hasEmptyDeviceList(user.getRealJid())) {
									axolotlService.fetchDeviceIds(user.getRealJid());
								}
							}
						}
					}
				} else {
					success = false;
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": could not request affiliation " + affiliations[i] + " in " + conversation.getJid().asBareJid());
				}
				++i;
				if (i >= affiliations.length) {
					List<Jid> members = conversation.getMucOptions().getMembers();
					if (success) {
						List<Jid> cryptoTargets = conversation.getAcceptedCryptoTargets();
						boolean changed = false;
						for (ListIterator<Jid> iterator = cryptoTargets.listIterator(); iterator.hasNext(); ) {
							Jid jid = iterator.next();
							if (!members.contains(jid)) {
								iterator.remove();
								Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": removed " + jid + " from crypto targets of " + conversation.getName());
								changed = true;
							}
						}
						if (changed) {
							conversation.setAcceptedCryptoTargets(cryptoTargets);
							updateConversation(conversation);
						}
					}
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": retrieved members for " + conversation.getJid().asBareJid() + ": " + conversation.getMucOptions().getMembers());
					getAvatarService().clear(conversation);
					updateMucRosterUi();
					updateConversationUi();
				}
			}
		};
		for (String affiliation : affiliations) {
			sendIqPacket(account, mIqGenerator.queryAffiliation(conversation, affiliation), callback);
		}
		Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": fetching members for " + conversation.getName());
	}

	public void providePasswordForMuc(Conversation conversation, String password) {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			conversation.getMucOptions().setPassword(password);
			if (conversation.getBookmark() != null) {
				if (respectAutojoin()) {
					conversation.getBookmark().setAutojoin(true);
				}
				pushBookmarks(conversation.getAccount());
			}
			updateConversation(conversation);
			joinMuc(conversation);
		}
	}

	private boolean hasEnabledAccounts() {
		for (Account account : this.accounts) {
			if (account.isEnabled()) {
				return true;
			}
		}
		return false;
	}

	public void persistSelfNick(MucOptions.User self) {
		final Conversation conversation = self.getConversation();
		Jid full = self.getFullJid();
		if (!full.equals(conversation.getJid())) {
			Log.d(Config.LOGTAG, "nick changed. updating");
			conversation.setContactJid(full);
			databaseBackend.updateConversation(conversation);
		}

		Bookmark bookmark = conversation.getBookmark();
		if (bookmark != null && !full.getResource().equals(bookmark.getNick())) {
			bookmark.setNick(full.getResource());
			pushBookmarks(bookmark.getAccount());
		}
	}

	public boolean renameInMuc(final Conversation conversation, final String nick, final UiCallback<Conversation> callback) {
		final MucOptions options = conversation.getMucOptions();
		final Jid joinJid = options.createJoinJid(nick);
		if (joinJid == null) {
			return false;
		}
		if (options.online()) {
			Account account = conversation.getAccount();
			options.setOnRenameListener(new OnRenameListener() {

				@Override
				public void onSuccess() {
					callback.success(conversation);
				}

				@Override
				public void onFailure() {
					callback.error(R.string.nick_in_use, conversation);
				}
			});

			PresencePacket packet = new PresencePacket();
			packet.setTo(joinJid);
			packet.setFrom(conversation.getAccount().getJid());

			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			sendPresencePacket(account, packet);
		} else {
			conversation.setContactJid(joinJid);
			databaseBackend.updateConversation(conversation);
			if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
				Bookmark bookmark = conversation.getBookmark();
				if (bookmark != null) {
					bookmark.setNick(nick);
					pushBookmarks(bookmark.getAccount());
				}
				joinMuc(conversation);
			}
		}
		return true;
	}

	public void leaveMuc(Conversation conversation) {
		leaveMuc(conversation, false);
	}

	private void leaveMuc(Conversation conversation, boolean now) {
		Account account = conversation.getAccount();
		account.pendingConferenceJoins.remove(conversation);
		account.pendingConferenceLeaves.remove(conversation);
		if (account.getStatus() == Account.State.ONLINE || now) {
			sendPresencePacket(conversation.getAccount(), mPresenceGenerator.leave(conversation.getMucOptions()));
			conversation.getMucOptions().setOffline();
			Bookmark bookmark = conversation.getBookmark();
			if (bookmark != null) {
				bookmark.setConversation(null);
			}
			Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": leaving muc " + conversation.getJid());
		} else {
			account.pendingConferenceLeaves.add(conversation);
		}
	}

	public String findConferenceServer(final Account account) {
		String server;
		if (account.getXmppConnection() != null) {
			server = account.getXmppConnection().getMucServer();
			if (server != null) {
				return server;
			}
		}
		for (Account other : getAccounts()) {
			if (other != account && other.getXmppConnection() != null) {
				server = other.getXmppConnection().getMucServer();
				if (server != null) {
					return server;
				}
			}
		}
		return null;
	}

	public boolean createAdhocConference(final Account account,
	                                     final String subject,
	                                     final Iterable<Jid> jids,
	                                     final UiCallback<Conversation> callback) {
		Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": creating adhoc conference with " + jids.toString());
		if (account.getStatus() == Account.State.ONLINE) {
			try {
				String server = findConferenceServer(account);
				if (server == null) {
					if (callback != null) {
						callback.error(R.string.no_conference_server_found, null);
					}
					return false;
				}
				final Jid jid = Jid.of(new BigInteger(64, getRNG()).toString(Character.MAX_RADIX), server, null);
				final Conversation conversation = findOrCreateConversation(account, jid, true, false, true);
				joinMuc(conversation, new OnConferenceJoined() {
					@Override
					public void onConferenceJoined(final Conversation conversation) {
						pushConferenceConfiguration(conversation, IqGenerator.defaultRoomConfiguration(), new OnConfigurationPushed() {
							@Override
							public void onPushSucceeded() {
								if (subject != null && !subject.trim().isEmpty()) {
									pushSubjectToConference(conversation, subject.trim());
								}
								for (Jid invite : jids) {
									invite(conversation, invite);
								}
								if (account.countPresences() > 1) {
									directInvite(conversation, account.getJid().asBareJid());
								}
								saveConversationAsBookmark(conversation, subject);
								if (callback != null) {
									callback.success(conversation);
								}
							}

							@Override
							public void onPushFailed() {
								archiveConversation(conversation);
								if (callback != null) {
									callback.error(R.string.conference_creation_failed, conversation);
								}
							}
						});
					}
				});
				return true;
			} catch (IllegalArgumentException e) {
				if (callback != null) {
					callback.error(R.string.conference_creation_failed, null);
				}
				return false;
			}
		} else {
			if (callback != null) {
				callback.error(R.string.not_connected_try_again, null);
			}
			return false;
		}
	}

	public void fetchConferenceConfiguration(final Conversation conversation) {
		fetchConferenceConfiguration(conversation, null);
	}

	public void fetchConferenceConfiguration(final Conversation conversation, final OnConferenceConfigurationFetched callback) {
		IqPacket request = new IqPacket(IqPacket.TYPE.GET);
		request.setTo(conversation.getJid().asBareJid());
		request.query("http://jabber.org/protocol/disco#info");
		sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Element query = packet.findChild("query", "http://jabber.org/protocol/disco#info");
				if (packet.getType() == IqPacket.TYPE.RESULT && query != null) {
					ArrayList<String> features = new ArrayList<>();
					for (Element child : query.getChildren()) {
						if (child != null && child.getName().equals("feature")) {
							String var = child.getAttribute("var");
							if (var != null) {
								features.add(var);
							}
						}
					}
					Element form = query.findChild("x", Namespace.DATA);
					Data data = form == null ? null : Data.parse(form);
					if (conversation.getMucOptions().updateConfiguration(features, data)) {
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": muc configuration changed for " + conversation.getJid().asBareJid());
						updateConversation(conversation);
					}
					if (callback != null) {
						callback.onConferenceConfigurationFetched(conversation);
					}
					updateConversationUi();
				} else if (packet.getType() == IqPacket.TYPE.ERROR) {
					if (callback != null) {
						callback.onFetchFailed(conversation, packet.getError());
					}
				}
			}
		});
	}

	public void pushNodeConfiguration(Account account, final String node, final Bundle options, final OnConfigurationPushed callback) {
		pushNodeConfiguration(account, account.getJid().asBareJid(), node, options, callback);
	}

	public void pushNodeConfiguration(Account account, final Jid jid, final String node, final Bundle options, final OnConfigurationPushed callback) {
		sendIqPacket(account, mIqGenerator.requestPubsubConfiguration(jid, node), new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
					Element configuration = pubsub == null ? null : pubsub.findChild("configure");
					Element x = configuration == null ? null : configuration.findChild("x", Namespace.DATA);
					if (x != null) {
						Data data = Data.parse(x);
						data.submit(options);
						sendIqPacket(account, mIqGenerator.publishPubsubConfiguration(jid, node, data), new OnIqPacketReceived() {
							@Override
							public void onIqPacketReceived(Account account, IqPacket packet) {
								if (packet.getType() == IqPacket.TYPE.RESULT && callback != null) {
									callback.onPushSucceeded();
								} else {
									Log.d(Config.LOGTAG, packet.toString());
								}
							}
						});
					} else if (callback != null) {
						callback.onPushFailed();
					}
				} else if (callback != null) {
					callback.onPushFailed();
				}
			}
		});
	}

	public void pushConferenceConfiguration(final Conversation conversation, final Bundle options, final OnConfigurationPushed callback) {
		IqPacket request = new IqPacket(IqPacket.TYPE.GET);
		request.setTo(conversation.getJid().asBareJid());
		request.query("http://jabber.org/protocol/muc#owner");
		sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Data data = Data.parse(packet.query().findChild("x", Namespace.DATA));
					data.submit(options);
					IqPacket set = new IqPacket(IqPacket.TYPE.SET);
					set.setTo(conversation.getJid().asBareJid());
					set.query("http://jabber.org/protocol/muc#owner").addChild(data);
					sendIqPacket(account, set, new OnIqPacketReceived() {
						@Override
						public void onIqPacketReceived(Account account, IqPacket packet) {
							if (callback != null) {
								if (packet.getType() == IqPacket.TYPE.RESULT) {
									callback.onPushSucceeded();
								} else {
									callback.onPushFailed();
								}
							}
						}
					});
				} else {
					if (callback != null) {
						callback.onPushFailed();
					}
				}
			}
		});
	}

	public void pushSubjectToConference(final Conversation conference, final String subject) {
		MessagePacket packet = this.getMessageGenerator().conferenceSubject(conference, subject);
		this.sendMessagePacket(conference.getAccount(), packet);
		final MucOptions mucOptions = conference.getMucOptions();
		final MucOptions.User self = mucOptions.getSelf();
		if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
			Bundle options = new Bundle();
			options.putString("muc#roomconfig_persistentroom", "1");
			options.putString("muc#roomconfig_roomname", subject);
			this.pushConferenceConfiguration(conference, options, null);
		}
	}

	public void changeAffiliationInConference(final Conversation conference, Jid user, final MucOptions.Affiliation affiliation, final OnAffiliationChanged callback) {
		final Jid jid = user.asBareJid();
		IqPacket request = this.mIqGenerator.changeAffiliation(conference, jid, affiliation.toString());
		sendIqPacket(conference.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					conference.getMucOptions().changeAffiliation(jid, affiliation);
					getAvatarService().clear(conference);
					callback.onAffiliationChangedSuccessful(jid);
				} else {
					callback.onAffiliationChangeFailed(jid, R.string.could_not_change_affiliation);
				}
			}
		});
	}

	public void changeAffiliationsInConference(final Conversation conference, MucOptions.Affiliation before, MucOptions.Affiliation after) {
		List<Jid> jids = new ArrayList<>();
		for (MucOptions.User user : conference.getMucOptions().getUsers()) {
			if (user.getAffiliation() == before && user.getRealJid() != null) {
				jids.add(user.getRealJid());
			}
		}
		IqPacket request = this.mIqGenerator.changeAffiliation(conference, jids, after.toString());
		sendIqPacket(conference.getAccount(), request, mDefaultIqHandler);
	}

	public void changeRoleInConference(final Conversation conference, final String nick, MucOptions.Role role, final OnRoleChanged callback) {
		IqPacket request = this.mIqGenerator.changeRole(conference, nick, role.toString());
		Log.d(Config.LOGTAG, request.toString());
		sendIqPacket(conference.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Log.d(Config.LOGTAG, packet.toString());
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					callback.onRoleChangedSuccessful(nick);
				} else {
					callback.onRoleChangeFailed(nick, R.string.could_not_change_role);
				}
			}
		});
	}

	private void disconnect(Account account, boolean force) {
		if ((account.getStatus() == Account.State.ONLINE)
				|| (account.getStatus() == Account.State.DISABLED)) {
			final XmppConnection connection = account.getXmppConnection();
			if (!force) {
				List<Conversation> conversations = getConversations();
				for (Conversation conversation : conversations) {
					if (conversation.getAccount() == account) {
						if (conversation.getMode() == Conversation.MODE_MULTI) {
							leaveMuc(conversation, true);
						}
					}
				}
				sendOfflinePresence(account);
			}
			connection.disconnect(force);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void updateMessage(Message message) {
		databaseBackend.updateMessage(message);
		updateConversationUi();
	}

	public void updateMessage(Message message, String uuid) {
		databaseBackend.updateMessage(message, uuid);
		updateConversationUi();
	}

	protected void syncDirtyContacts(Account account) {
		for (Contact contact : account.getRoster().getContacts()) {
			if (contact.getOption(Contact.Options.DIRTY_PUSH)) {
				pushContactToServer(contact);
			}
			if (contact.getOption(Contact.Options.DIRTY_DELETE)) {
				deleteContactOnServer(contact);
			}
		}
	}

	public void createContact(Contact contact, boolean autoGrant) {
		if (autoGrant) {
			contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
			contact.setOption(Contact.Options.ASKING);
		}
		pushContactToServer(contact);
	}

	public void pushContactToServer(final Contact contact) {
		contact.resetOption(Contact.Options.DIRTY_DELETE);
		contact.setOption(Contact.Options.DIRTY_PUSH);
		final Account account = contact.getAccount();
		if (account.getStatus() == Account.State.ONLINE) {
			final boolean ask = contact.getOption(Contact.Options.ASKING);
			final boolean sendUpdates = contact
					.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
					&& contact.getOption(Contact.Options.PREEMPTIVE_GRANT);
			final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
			iq.query(Namespace.ROSTER).addChild(contact.asElement());
			account.getXmppConnection().sendIqPacket(iq, mDefaultIqHandler);
			if (sendUpdates) {
				sendPresencePacket(account, mPresenceGenerator.sendPresenceUpdatesTo(contact));
			}
			if (ask) {
				sendPresencePacket(account, mPresenceGenerator.requestPresenceUpdatesFrom(contact));
			}
		} else {
			syncRoster(contact.getAccount());
		}
	}

	public void publishAvatar(final Account account, final Uri image, final UiCallback<Avatar> callback) {
		new Thread(() -> {
			final Bitmap.CompressFormat format = Config.AVATAR_FORMAT;
			final int size = Config.AVATAR_SIZE;
			final Avatar avatar = getFileBackend().getPepAvatar(image, size, format);
			if (avatar != null) {
				if (!getFileBackend().save(avatar)) {
					callback.error(R.string.error_saving_avatar, avatar);
					return;
				}
				publishAvatar(account, avatar, callback);
			} else {
				callback.error(R.string.error_publish_avatar_converting, null);
			}
		}).start();

	}

	public void publishAvatar(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.publishAvatar(avatar);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket result) {
				if (result.getType() == IqPacket.TYPE.RESULT) {
					final IqPacket packet = XmppConnectionService.this.mIqGenerator.publishAvatarMetadata(avatar);
					sendIqPacket(account, packet, new OnIqPacketReceived() {
						@Override
						public void onIqPacketReceived(Account account, IqPacket result) {
							if (result.getType() == IqPacket.TYPE.RESULT) {
								if (account.setAvatar(avatar.getFilename())) {
									getAvatarService().clear(account);
									databaseBackend.updateAccount(account);
								}
								Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": published avatar " + (avatar.size / 1024) + "KiB");
								if (callback != null) {
									callback.success(avatar);
								}
							} else {
								if (callback != null) {
									callback.error(R.string.error_publish_avatar_server_reject, avatar);
								}
							}
						}
					});
				} else {
					Element error = result.findChild("error");
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": server rejected avatar " + (avatar.size / 1024) + "KiB " + (error != null ? error.toString() : ""));
					if (callback != null) {
						callback.error(R.string.error_publish_avatar_server_reject, avatar);
					}
				}
			}
		});
	}

	public void republishAvatarIfNeeded(Account account) {
		if (account.getAxolotlService().isPepBroken()) {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": skipping republication of avatar because pep is broken");
			return;
		}
		IqPacket packet = this.mIqGenerator.retrieveAvatarMetaData(null);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {

			private Avatar parseAvatar(IqPacket packet) {
				Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub");
				if (pubsub != null) {
					Element items = pubsub.findChild("items");
					if (items != null) {
						return Avatar.parseMetadata(items);
					}
				}
				return null;
			}

			private boolean errorIsItemNotFound(IqPacket packet) {
				Element error = packet.findChild("error");
				return packet.getType() == IqPacket.TYPE.ERROR
						&& error != null
						&& error.hasChild("item-not-found");
			}

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT || errorIsItemNotFound(packet)) {
					Avatar serverAvatar = parseAvatar(packet);
					if (serverAvatar == null && account.getAvatar() != null) {
						Avatar avatar = fileBackend.getStoredPepAvatar(account.getAvatar());
						if (avatar != null) {
							Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": avatar on server was null. republishing");
							publishAvatar(account, fileBackend.getStoredPepAvatar(account.getAvatar()), null);
						} else {
							Log.e(Config.LOGTAG, account.getJid().asBareJid() + ": error rereading avatar");
						}
					}
				}
			}
		});
	}

	public void fetchAvatar(Account account, Avatar avatar) {
		fetchAvatar(account, avatar, null);
	}

	public void fetchAvatar(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		final String KEY = generateFetchKey(account, avatar);
		synchronized (this.mInProgressAvatarFetches) {
			if (!this.mInProgressAvatarFetches.contains(KEY)) {
				switch (avatar.origin) {
					case PEP:
						this.mInProgressAvatarFetches.add(KEY);
						fetchAvatarPep(account, avatar, callback);
						break;
					case VCARD:
						this.mInProgressAvatarFetches.add(KEY);
						fetchAvatarVcard(account, avatar, callback);
						break;
				}
			}
		}
	}

	private void fetchAvatarPep(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrievePepAvatar(avatar);
		sendIqPacket(account, packet, (a, result) -> {
			synchronized (mInProgressAvatarFetches) {
				mInProgressAvatarFetches.remove(generateFetchKey(a, avatar));
			}
			final String ERROR = a.getJid().asBareJid() + ": fetching avatar for " + avatar.owner + " failed ";
			if (result.getType() == IqPacket.TYPE.RESULT) {
				avatar.image = mIqParser.avatarData(result);
				if (avatar.image != null) {
					if (getFileBackend().save(avatar)) {
						if (a.getJid().asBareJid().equals(avatar.owner)) {
							if (a.setAvatar(avatar.getFilename())) {
								databaseBackend.updateAccount(a);
							}
							getAvatarService().clear(a);
							updateConversationUi();
							updateAccountUi();
						} else {
							Contact contact = a.getRoster().getContact(avatar.owner);
							contact.setAvatar(avatar);
							getAvatarService().clear(contact);
							updateConversationUi();
							updateRosterUi();
						}
						if (callback != null) {
							callback.success(avatar);
						}
						Log.d(Config.LOGTAG, a.getJid().asBareJid()
								+ ": successfully fetched pep avatar for " + avatar.owner);
						return;
					}
				} else {

					Log.d(Config.LOGTAG, ERROR + "(parsing error)");
				}
			} else {
				Element error = result.findChild("error");
				if (error == null) {
					Log.d(Config.LOGTAG, ERROR + "(server error)");
				} else {
					Log.d(Config.LOGTAG, ERROR + error.toString());
				}
			}
			if (callback != null) {
				callback.error(0, null);
			}

		});
	}

	private void fetchAvatarVcard(final Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrieveVcardAvatar(avatar);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				synchronized (mInProgressAvatarFetches) {
					mInProgressAvatarFetches.remove(generateFetchKey(account, avatar));
				}
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element vCard = packet.findChild("vCard", "vcard-temp");
					Element photo = vCard != null ? vCard.findChild("PHOTO") : null;
					String image = photo != null ? photo.findChildContent("BINVAL") : null;
					if (image != null) {
						avatar.image = image;
						if (getFileBackend().save(avatar)) {
							Log.d(Config.LOGTAG, account.getJid().asBareJid()
									+ ": successfully fetched vCard avatar for " + avatar.owner);
							if (avatar.owner.isBareJid()) {
								if (account.getJid().asBareJid().equals(avatar.owner) && account.getAvatar() == null) {
									Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": had no avatar. replacing with vcard");
									account.setAvatar(avatar.getFilename());
									databaseBackend.updateAccount(account);
									getAvatarService().clear(account);
									updateAccountUi();
								} else {
									Contact contact = account.getRoster().getContact(avatar.owner);
									contact.setAvatar(avatar);
									getAvatarService().clear(contact);
									updateRosterUi();
								}
								updateConversationUi();
							} else {
								Conversation conversation = find(account, avatar.owner.asBareJid());
								if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
									MucOptions.User user = conversation.getMucOptions().findUserByFullJid(avatar.owner);
									if (user != null) {
										if (user.setAvatar(avatar)) {
											getAvatarService().clear(user);
											updateConversationUi();
											updateMucRosterUi();
										}
									}
								}
							}
						}
					}
				}
			}
		});
	}

	public void checkForAvatar(Account account, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrieveAvatarMetaData(null);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub");
					if (pubsub != null) {
						Element items = pubsub.findChild("items");
						if (items != null) {
							Avatar avatar = Avatar.parseMetadata(items);
							if (avatar != null) {
								avatar.owner = account.getJid().asBareJid();
								if (fileBackend.isAvatarCached(avatar)) {
									if (account.setAvatar(avatar.getFilename())) {
										databaseBackend.updateAccount(account);
									}
									getAvatarService().clear(account);
									callback.success(avatar);
								} else {
									fetchAvatarPep(account, avatar, callback);
								}
								return;
							}
						}
					}
				}
				callback.error(0, null);
			}
		});
	}

	public void deleteContactOnServer(Contact contact) {
		contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
		contact.resetOption(Contact.Options.DIRTY_PUSH);
		contact.setOption(Contact.Options.DIRTY_DELETE);
		Account account = contact.getAccount();
		if (account.getStatus() == Account.State.ONLINE) {
			IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
			Element item = iq.query(Namespace.ROSTER).addChild("item");
			item.setAttribute("jid", contact.getJid().toString());
			item.setAttribute("subscription", "remove");
			account.getXmppConnection().sendIqPacket(iq, mDefaultIqHandler);
		}
	}

	public void updateConversation(final Conversation conversation) {
		mDatabaseWriterExecutor.execute(() -> databaseBackend.updateConversation(conversation));
	}

	private void reconnectAccount(final Account account, final boolean force, final boolean interactive) {
		synchronized (account) {
			XmppConnection connection = account.getXmppConnection();
			if (connection == null) {
				connection = createConnection(account);
				account.setXmppConnection(connection);
			}
			boolean hasInternet = hasInternetConnection();
			if (account.isEnabled() && hasInternet) {
				if (!force) {
					disconnect(account, false);
				}
				Thread thread = new Thread(connection);
				connection.setInteractive(interactive);
				connection.prepareNewConnection();
				connection.interrupt();
				thread.start();
				scheduleWakeUpCall(Config.CONNECT_DISCO_TIMEOUT, account.getUuid().hashCode());
			} else {
				disconnect(account, force || account.getTrueStatus().isError() || !hasInternet);
				account.getRoster().clearPresences();
				connection.resetEverything();
				final AxolotlService axolotlService = account.getAxolotlService();
				if (axolotlService != null) {
					axolotlService.resetBrokenness();
				}
				if (!hasInternet) {
					account.setStatus(Account.State.NO_INTERNET);
				}
			}
		}
	}

	public void reconnectAccountInBackground(final Account account) {
		new Thread(() -> reconnectAccount(account, false, true)).start();
	}

	public void invite(Conversation conversation, Jid contact) {
		Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": inviting " + contact + " to " + conversation.getJid().asBareJid());
		MessagePacket packet = mMessageGenerator.invite(conversation, contact);
		sendMessagePacket(conversation.getAccount(), packet);
	}

	public void directInvite(Conversation conversation, Jid jid) {
		MessagePacket packet = mMessageGenerator.directInvite(conversation, jid);
		sendMessagePacket(conversation.getAccount(), packet);
	}

	public void resetSendingToWaiting(Account account) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getAccount() == account) {
				conversation.findUnsentTextMessages(new Conversation.OnMessageFound() {

					@Override
					public void onMessageFound(Message message) {
						markMessage(message, Message.STATUS_WAITING);
					}
				});
			}
		}
	}

	public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status) {
		return markMessage(account, recipient, uuid, status, null);
	}

	public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status, String errorMessage) {
		if (uuid == null) {
			return null;
		}
		for (Conversation conversation : getConversations()) {
			if (conversation.getJid().asBareJid().equals(recipient) && conversation.getAccount() == account) {
				final Message message = conversation.findSentMessageWithUuidOrRemoteId(uuid);
				if (message != null) {
					markMessage(message, status, errorMessage);
				}
				return message;
			}
		}
		return null;
	}

	public boolean markMessage(Conversation conversation, String uuid, int status, String serverMessageId) {
		if (uuid == null) {
			return false;
		} else {
			Message message = conversation.findSentMessageWithUuid(uuid);
			if (message != null) {
				if (message.getServerMsgId() == null) {
					message.setServerMsgId(serverMessageId);
				}
				markMessage(message, status);
				return true;
			} else {
				return false;
			}
		}
	}

	public void markMessage(Message message, int status) {
		markMessage(message, status, null);
	}


	public void markMessage(Message message, int status, String errorMessage) {
		final int c = message.getStatus();
		if (status == Message.STATUS_SEND_FAILED && (c == Message.STATUS_SEND_RECEIVED || c == Message.STATUS_SEND_DISPLAYED)) {
			return;
		}
		if (status == Message.STATUS_SEND_RECEIVED && c == Message.STATUS_SEND_DISPLAYED) {
			return;
		}
		message.setErrorMessage(errorMessage);
		message.setStatus(status);
		databaseBackend.updateMessage(message);
		updateConversationUi();
	}

	private SharedPreferences getPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	}

	public long getAutomaticMessageDeletionDate() {
		final long timeout = getLongPreference(SettingsActivity.AUTOMATIC_MESSAGE_DELETION, R.integer.automatic_message_deletion);
		return timeout == 0 ? timeout : (System.currentTimeMillis() - (timeout * 1000));
	}

	public long getLongPreference(String name, @IntegerRes int res) {
		long defaultValue = getResources().getInteger(res);
		try {
			return Long.parseLong(getPreferences().getString(name, String.valueOf(defaultValue)));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public boolean getBooleanPreference(String name, @BoolRes int res) {
		return getPreferences().getBoolean(name, getResources().getBoolean(res));
	}

	public boolean confirmMessages() {
		return getBooleanPreference("confirm_messages", R.bool.confirm_messages);
	}

	public boolean allowMessageCorrection() {
		return getBooleanPreference("allow_message_correction", R.bool.allow_message_correction);
	}

	public boolean sendChatStates() {
		return getBooleanPreference("chat_states", R.bool.chat_states);
	}

	private boolean respectAutojoin() {
		return getBooleanPreference("autojoin", R.bool.autojoin);
	}

	public boolean indicateReceived() {
		return getBooleanPreference("indicate_received", R.bool.indicate_received);
	}

	public boolean useTorToConnect() {
		return Config.FORCE_ORBOT || getBooleanPreference("use_tor", R.bool.use_tor);
	}

	public boolean showExtendedConnectionOptions() {
		return getBooleanPreference("show_connection_options", R.bool.show_connection_options);
	}

	public boolean broadcastLastActivity() {
		return getBooleanPreference(SettingsActivity.BROADCAST_LAST_ACTIVITY, R.bool.last_activity);
	}

	public int unreadCount() {
		int count = 0;
		for (Conversation conversation : getConversations()) {
			count += conversation.unreadCount();
		}
		return count;
	}


	public void showErrorToastInUi(int resId) {
		if (mOnShowErrorToast != null) {
			mOnShowErrorToast.onShowErrorToast(resId);
		}
	}

	public void updateConversationUi() {
		if (mOnConversationUpdate != null) {
			mOnConversationUpdate.onConversationUpdate();
		}
	}

	public void updateAccountUi() {
		if (mOnAccountUpdate != null) {
			mOnAccountUpdate.onAccountUpdate();
		}
	}

	public void updateRosterUi() {
		if (mOnRosterUpdate != null) {
			mOnRosterUpdate.onRosterUpdate();
		}
	}

	public boolean displayCaptchaRequest(Account account, String id, Data data, Bitmap captcha) {
		if (mOnCaptchaRequested != null) {
			DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
			Bitmap scaled = Bitmap.createScaledBitmap(captcha, (int) (captcha.getWidth() * metrics.scaledDensity),
					(int) (captcha.getHeight() * metrics.scaledDensity), false);

			mOnCaptchaRequested.onCaptchaRequested(account, id, data, scaled);
			return true;
		}
		return false;
	}

	public void updateBlocklistUi(final OnUpdateBlocklist.Status status) {
		if (mOnUpdateBlocklist != null) {
			mOnUpdateBlocklist.OnUpdateBlocklist(status);
		}
	}

	public void updateMucRosterUi() {
		if (mOnMucRosterUpdate != null) {
			mOnMucRosterUpdate.onMucRosterUpdate();
		}
	}

	public void keyStatusUpdated(AxolotlService.FetchStatus report) {
		if (mOnKeyStatusUpdated != null) {
			mOnKeyStatusUpdated.onKeyStatusUpdated(report);
		}
	}

	public Account findAccountByJid(final Jid accountJid) {
		for (Account account : this.accounts) {
			if (account.getJid().asBareJid().equals(accountJid.asBareJid())) {
				return account;
			}
		}
		return null;
	}

	public Conversation findConversationByUuid(String uuid) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getUuid().equals(uuid)) {
				return conversation;
			}
		}
		return null;
	}

	public boolean markRead(final Conversation conversation, boolean dismiss) {
		return markRead(conversation, null, dismiss).size() > 0;
	}

	public void markRead(final Conversation conversation) {
		markRead(conversation, null, true);
	}

	public List<Message> markRead(final Conversation conversation, String upToUuid, boolean dismiss) {
		if (dismiss) {
			mNotificationService.clear(conversation);
		}
		final List<Message> readMessages = conversation.markRead(upToUuid);
		if (readMessages.size() > 0) {
			Runnable runnable = () -> {
				for (Message message : readMessages) {
					databaseBackend.updateMessage(message);
				}
			};
			mDatabaseWriterExecutor.execute(runnable);
			updateUnreadCountBadge();
			return readMessages;
		} else {
			return readMessages;
		}
	}

	public synchronized void updateUnreadCountBadge() {
		int count = unreadCount();
		if (unreadCount != count) {
			Log.d(Config.LOGTAG, "update unread count to " + count);
			if (count > 0) {
				ShortcutBadger.applyCount(getApplicationContext(), count);
			} else {
				ShortcutBadger.removeCount(getApplicationContext());
			}
			unreadCount = count;
		}
	}

	public void sendReadMarker(final Conversation conversation, String upToUuid) {
		final boolean isPrivateAndNonAnonymousMuc = conversation.getMode() == Conversation.MODE_MULTI && conversation.isPrivateAndNonAnonymous();
		final List<Message> readMessages = this.markRead(conversation, upToUuid, true);
		if (readMessages.size() > 0) {
			updateConversationUi();
		}
		final Message markable = Conversation.getLatestMarkableMessage(readMessages, isPrivateAndNonAnonymousMuc);
		if (confirmMessages()
				&& markable != null
				&& (markable.trusted() || isPrivateAndNonAnonymousMuc)
				&& markable.getRemoteMsgId() != null) {
			Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": sending read marker to " + markable.getCounterpart().toString());
			Account account = conversation.getAccount();
			final Jid to = markable.getCounterpart();
			final boolean groupChat = conversation.getMode() == Conversation.MODE_MULTI;
			MessagePacket packet = mMessageGenerator.confirm(account, to, markable.getRemoteMsgId(), markable.getCounterpart(), groupChat);
			this.sendMessagePacket(conversation.getAccount(), packet);
		}
	}

	public SecureRandom getRNG() {
		return this.mRandom;
	}

	public MemorizingTrustManager getMemorizingTrustManager() {
		return this.mMemorizingTrustManager;
	}

	public void setMemorizingTrustManager(MemorizingTrustManager trustManager) {
		this.mMemorizingTrustManager = trustManager;
	}

	public void updateMemorizingTrustmanager() {
		final MemorizingTrustManager tm;
		final boolean dontTrustSystemCAs = getBooleanPreference("dont_trust_system_cas", R.bool.dont_trust_system_cas);
		if (dontTrustSystemCAs) {
			tm = new MemorizingTrustManager(getApplicationContext(), null);
		} else {
			tm = new MemorizingTrustManager(getApplicationContext());
		}
		setMemorizingTrustManager(tm);
	}

	public LruCache<String, Bitmap> getBitmapCache() {
		return this.mBitmapCache;
	}

	public Collection<String> getKnownHosts() {
		final Set<String> hosts = new HashSet<>();
		for (final Account account : getAccounts()) {
			hosts.add(account.getServer());
			for (final Contact contact : account.getRoster().getContacts()) {
				if (contact.showInRoster()) {
					final String server = contact.getServer();
					if (server != null && !hosts.contains(server)) {
						hosts.add(server);
					}
				}
			}
		}
		if (Config.DOMAIN_LOCK != null) {
			hosts.add(Config.DOMAIN_LOCK);
		}
		if (Config.MAGIC_CREATE_DOMAIN != null) {
			hosts.add(Config.MAGIC_CREATE_DOMAIN);
		}
		return hosts;
	}

	public Collection<String> getKnownConferenceHosts() {
		final Set<String> mucServers = new HashSet<>();
		for (final Account account : accounts) {
			if (account.getXmppConnection() != null) {
				mucServers.addAll(account.getXmppConnection().getMucServers());
				for (Bookmark bookmark : account.getBookmarks()) {
					final Jid jid = bookmark.getJid();
					final String s = jid == null ? null : jid.getDomain();
					if (s != null) {
						mucServers.add(s);
					}
				}
			}
		}
		return mucServers;
	}

	public void sendMessagePacket(Account account, MessagePacket packet) {
		XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendMessagePacket(packet);
		}
	}

	public void sendPresencePacket(Account account, PresencePacket packet) {
		XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendPresencePacket(packet);
		}
	}

	public void sendCreateAccountWithCaptchaPacket(Account account, String id, Data data) {
		final XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			IqPacket request = mIqGenerator.generateCreateAccountWithCaptcha(account, id, data);
			connection.sendUnmodifiedIqPacket(request, connection.registrationResponseListener, true);
		}
	}

	public void sendIqPacket(final Account account, final IqPacket packet, final OnIqPacketReceived callback) {
		final XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendIqPacket(packet, callback);
		}
	}

	public void sendPresence(final Account account) {
		sendPresence(account, checkListeners() && broadcastLastActivity());
	}

	private void sendPresence(final Account account, final boolean includeIdleTimestamp) {
		Presence.Status status;
		if (manuallyChangePresence()) {
			status = account.getPresenceStatus();
		} else {
			status = getTargetPresence();
		}
		PresencePacket packet = mPresenceGenerator.selfPresence(account, status);
		String message = account.getPresenceStatusMessage();
		if (message != null && !message.isEmpty()) {
			packet.addChild(new Element("status").setContent(message));
		}
		if (mLastActivity > 0 && includeIdleTimestamp) {
			long since = Math.min(mLastActivity, System.currentTimeMillis()); //don't send future dates
			packet.addChild("idle", Namespace.IDLE).setAttribute("since", AbstractGenerator.getTimestamp(since));
		}
		sendPresencePacket(account, packet);
	}

	private void deactivateGracePeriod() {
		for (Account account : getAccounts()) {
			account.deactivateGracePeriod();
		}
	}

	public void refreshAllPresences() {
		boolean includeIdleTimestamp = checkListeners() && broadcastLastActivity();
		for (Account account : getAccounts()) {
			if (account.isEnabled()) {
				sendPresence(account, includeIdleTimestamp);
			}
		}
	}

	private void refreshAllGcmTokens() {
		for (Account account : getAccounts()) {
			/*if (account.isOnlineAndConnected() && mPushManagementService.available(account)) {
				mPushManagementService.registerPushTokenOnServer(account);
			}*/
		}
	}

	private void sendOfflinePresence(final Account account) {
		Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending offline presence");
		sendPresencePacket(account, mPresenceGenerator.sendOfflinePresence(account));
	}

	public MessageGenerator getMessageGenerator() {
		return this.mMessageGenerator;
	}

	public PresenceGenerator getPresenceGenerator() {
		return this.mPresenceGenerator;
	}

	public IqGenerator getIqGenerator() {
		return this.mIqGenerator;
	}

	public IqParser getIqParser() {
		return this.mIqParser;
	}

	public JingleConnectionManager getJingleConnectionManager() {
		return this.mJingleConnectionManager;
	}

	public MessageArchiveService getMessageArchiveService() {
		return this.mMessageArchiveService;
	}

	public List<Contact> findContacts(Jid jid, String accountJid) {
		ArrayList<Contact> contacts = new ArrayList<>();
		for (Account account : getAccounts()) {
			if ((account.isEnabled() || accountJid != null)
					&& (accountJid == null || accountJid.equals(account.getJid().asBareJid().toString()))) {
				Contact contact = account.getRoster().getContactFromRoster(jid);
				if (contact != null) {
					contacts.add(contact);
				}
			}
		}
		return contacts;
	}

	public Conversation findFirstMuc(Jid jid) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getAccount().isEnabled() && conversation.getJid().asBareJid().equals(jid.asBareJid()) && conversation.getMode() == Conversation.MODE_MULTI) {
				return conversation;
			}
		}
		return null;
	}

	public NotificationService getNotificationService() {
		return this.mNotificationService;
	}

	public HttpConnectionManager getHttpConnectionManager() {
		return this.mHttpConnectionManager;
	}

	public void resendFailedMessages(final Message message) {
		final Collection<Message> messages = new ArrayList<>();
		Message current = message;
		while (current.getStatus() == Message.STATUS_SEND_FAILED) {
			messages.add(current);
			if (current.mergeable(current.next())) {
				current = current.next();
			} else {
				break;
			}
		}
		for (final Message msg : messages) {
			msg.setTime(System.currentTimeMillis());
			markMessage(msg, Message.STATUS_WAITING);
			this.resendMessage(msg, false);
		}
		message.getConversation().sort();
		updateConversationUi();
	}

	public void clearConversationHistory(final Conversation conversation) {
		final long clearDate;
		final String reference;
		if (conversation.countMessages() > 0) {
			Message latestMessage = conversation.getLatestMessage();
			clearDate = latestMessage.getTimeSent() + 1000;
			reference = latestMessage.getServerMsgId();
		} else {
			clearDate = System.currentTimeMillis();
			reference = null;
		}
		conversation.clearMessages();
		conversation.setHasMessagesLeftOnServer(false); //avoid messages getting loaded through mam
		conversation.setLastClearHistory(clearDate, reference);
		Runnable runnable = () -> {
			databaseBackend.deleteMessagesInConversation(conversation);
			databaseBackend.updateConversation(conversation);
		};
		mDatabaseWriterExecutor.execute(runnable);
	}

	public boolean sendBlockRequest(final Blockable blockable, boolean reportSpam) {
		if (blockable != null && blockable.getBlockedJid() != null) {
			final Jid jid = blockable.getBlockedJid();
			this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetBlockRequest(jid, reportSpam), new OnIqPacketReceived() {

				@Override
				public void onIqPacketReceived(final Account account, final IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						account.getBlocklist().add(jid);
						updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
					}
				}
			});
			if (removeBlockedConversations(blockable.getAccount(), jid)) {
				updateConversationUi();
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean removeBlockedConversations(final Account account, final Jid blockedJid) {
		boolean removed = false;
		synchronized (this.conversations) {
			boolean domainJid = blockedJid.getLocal() == null;
			for (Conversation conversation : this.conversations) {
				boolean jidMatches = (domainJid && blockedJid.getDomain().equals(conversation.getJid().getDomain()))
						|| blockedJid.equals(conversation.getJid().asBareJid());
				if (conversation.getAccount() == account
						&& conversation.getMode() == Conversation.MODE_SINGLE
						&& jidMatches) {
					this.conversations.remove(conversation);
					markRead(conversation);
					conversation.setStatus(Conversation.STATUS_ARCHIVED);
					Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": archiving conversation " + conversation.getJid().asBareJid() + " because jid was blocked");
					updateConversation(conversation);
					removed = true;
				}
			}
		}
		return removed;
	}

	public void sendUnblockRequest(final Blockable blockable) {
		if (blockable != null && blockable.getJid() != null) {
			final Jid jid = blockable.getBlockedJid();
			this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetUnblockRequest(jid), new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(final Account account, final IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						account.getBlocklist().remove(jid);
						updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
					}
				}
			});
		}
	}

	public void publishDisplayName(Account account) {
		String displayName = account.getDisplayName();
		if (displayName != null && !displayName.isEmpty()) {
			IqPacket publish = mIqGenerator.publishNick(displayName);
			sendIqPacket(account, publish, (account1, packet) -> {
				if (packet.getType() == IqPacket.TYPE.ERROR) {
					Log.d(Config.LOGTAG, account1.getJid().asBareJid() + ": could not publish nick");
				}
			});
		}
	}

	public ServiceDiscoveryResult getCachedServiceDiscoveryResult(Pair<String, String> key) {
		ServiceDiscoveryResult result = discoCache.get(key);
		if (result != null) {
			return result;
		} else {
			result = databaseBackend.findDiscoveryResult(key.first, key.second);
			if (result != null) {
				discoCache.put(key, result);
			}
			return result;
		}
	}

	public void fetchCaps(Account account, final Jid jid, final Presence presence) {
		final Pair<String, String> key = new Pair<>(presence.getHash(), presence.getVer());
		ServiceDiscoveryResult disco = getCachedServiceDiscoveryResult(key);
		if (disco != null) {
			presence.setServiceDiscoveryResult(disco);
		} else {
			if (!account.inProgressDiscoFetches.contains(key)) {
				account.inProgressDiscoFetches.add(key);
				IqPacket request = new IqPacket(IqPacket.TYPE.GET);
				request.setTo(jid);
				String node = presence.getNode();
				Element query = request.query("http://jabber.org/protocol/disco#info");
				if (node != null) {
					query.setAttribute("node",node);
				}
				Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": making disco request for " + key.second + " to " + jid+ "node="+node);
				sendIqPacket(account, request, (a, discoPacket) -> {
					if (discoPacket.getType() == IqPacket.TYPE.RESULT) {
						ServiceDiscoveryResult disco1 = new ServiceDiscoveryResult(discoPacket);
						if (presence.getVer().equals(disco1.getVer())) {
							databaseBackend.insertDiscoveryResult(disco1);
							injectServiceDiscorveryResult(a.getRoster(), presence.getHash(), presence.getVer(), disco1);
						} else {
							Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": mismatch in caps for contact " + jid + " " + presence.getVer() + " vs " + disco1.getVer());
						}
					}
					a.inProgressDiscoFetches.remove(key);
				});
			}
		}
	}

	private void injectServiceDiscorveryResult(Roster roster, String hash, String ver, ServiceDiscoveryResult disco) {
		for (Contact contact : roster.getContacts()) {
			for (Presence presence : contact.getPresences().getPresences().values()) {
				if (hash.equals(presence.getHash()) && ver.equals(presence.getVer())) {
					presence.setServiceDiscoveryResult(disco);
				}
			}
		}
	}

	public void fetchMamPreferences(Account account, final OnMamPreferencesFetched callback) {
		final boolean legacy = account.getXmppConnection().getFeatures().mamLegacy();
		IqPacket request = new IqPacket(IqPacket.TYPE.GET);
		request.addChild("prefs", legacy ? Namespace.MAM_LEGACY : Namespace.MAM);
		sendIqPacket(account, request, (account1, packet) -> {
			Element prefs = packet.findChild("prefs", legacy ? Namespace.MAM_LEGACY : Namespace.MAM);
			if (packet.getType() == IqPacket.TYPE.RESULT && prefs != null) {
				callback.onPreferencesFetched(prefs);
			} else {
				callback.onPreferencesFetchFailed();
			}
		});
	}

	/*public PushManagementService getPushManagementService() {
		return mPushManagementService;
	}*/

	public Account getPendingAccount() {
		Account pending = null;
		for (Account account : getAccounts()) {
			if (!account.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
				pending = account;
			} else {
				return null;
			}
		}
		return pending;
	}

	public void changeStatus(Account account, PresenceTemplate template, String signature) {
		if (!template.getStatusMessage().isEmpty()) {
			databaseBackend.insertPresenceTemplate(template);
		}
		account.setPgpSignature(signature);
		account.setPresenceStatus(template.getStatus());
		account.setPresenceStatusMessage(template.getStatusMessage());
		databaseBackend.updateAccount(account);
		sendPresence(account);
	}

	public List<PresenceTemplate> getPresenceTemplates(Account account) {
		List<PresenceTemplate> templates = databaseBackend.getPresenceTemplates();
		for (PresenceTemplate template : account.getSelfContact().getPresences().asTemplates()) {
			if (!templates.contains(template)) {
				templates.add(0, template);
			}
		}
		return templates;
	}

	public void saveConversationAsBookmark(Conversation conversation, String name) {
		Account account = conversation.getAccount();
		Bookmark bookmark = new Bookmark(account, conversation.getJid().asBareJid());
		if (!conversation.getJid().isBareJid()) {
			bookmark.setNick(conversation.getJid().getResource());
		}
		if (name != null && !name.trim().isEmpty()) {
			bookmark.setBookmarkName(name.trim());
		}
		bookmark.setAutojoin(getPreferences().getBoolean("autojoin", getResources().getBoolean(R.bool.autojoin)));
		account.getBookmarks().add(bookmark);
		pushBookmarks(account);
		bookmark.setConversation(conversation);
	}

	public boolean verifyFingerprints(Contact contact, List<XmppUri.Fingerprint> fingerprints) {
		boolean performedVerification = false;
		final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
		for (XmppUri.Fingerprint fp : fingerprints) {
			if (fp.type == XmppUri.FingerprintType.OMEMO) {
				String fingerprint = "05" + fp.fingerprint.replaceAll("\\s", "");
				FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprint);
				if (fingerprintStatus != null) {
					if (!fingerprintStatus.isVerified()) {
						performedVerification = true;
						axolotlService.setFingerprintTrust(fingerprint, fingerprintStatus.toVerified());
					}
				} else {
					axolotlService.preVerifyFingerprint(contact, fingerprint);
				}
			}
		}
		return performedVerification;
	}

	public boolean verifyFingerprints(Account account, List<XmppUri.Fingerprint> fingerprints) {
		final AxolotlService axolotlService = account.getAxolotlService();
		boolean verifiedSomething = false;
		for (XmppUri.Fingerprint fp : fingerprints) {
			if (fp.type == XmppUri.FingerprintType.OMEMO) {
				String fingerprint = "05" + fp.fingerprint.replaceAll("\\s", "");
				Log.d(Config.LOGTAG, "trying to verify own fp=" + fingerprint);
				FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprint);
				if (fingerprintStatus != null) {
					if (!fingerprintStatus.isVerified()) {
						axolotlService.setFingerprintTrust(fingerprint, fingerprintStatus.toVerified());
						verifiedSomething = true;
					}
				} else {
					axolotlService.preVerifyFingerprint(account, fingerprint);
					verifiedSomething = true;
				}
			}
		}
		return verifiedSomething;
	}

	public boolean blindTrustBeforeVerification() {
		return getBooleanPreference(SettingsActivity.BLIND_TRUST_BEFORE_VERIFICATION, R.bool.btbv);
	}

	public ShortcutService getShortcutService() {
		return mShortcutService;
	}

	public void pushMamPreferences(Account account, Element prefs) {
		IqPacket set = new IqPacket(IqPacket.TYPE.SET);
		set.addChild(prefs);
		sendIqPacket(account, set, null);
	}

	public interface OnMamPreferencesFetched {
		void onPreferencesFetched(Element prefs);

		void onPreferencesFetchFailed();
	}

	public interface OnAccountCreated {
		void onAccountCreated(Account account);

		void informUser(int r);
	}

	public interface OnMoreMessagesLoaded {
		void onMoreMessagesLoaded(int count, Conversation conversation);

		void informUser(int r);
	}

	public interface OnAccountPasswordChanged {
		void onPasswordChangeSucceeded();

		void onPasswordChangeFailed();
	}

	public interface OnAffiliationChanged {
		void onAffiliationChangedSuccessful(Jid jid);

		void onAffiliationChangeFailed(Jid jid, int resId);
	}

	public interface OnRoleChanged {
		void onRoleChangedSuccessful(String nick);

		void onRoleChangeFailed(String nick, int resid);
	}

	public interface OnConversationUpdate {
		void onConversationUpdate();
	}

	public interface OnAccountUpdate {
		void onAccountUpdate();
	}

	public interface OnCaptchaRequested {
		void onCaptchaRequested(Account account, String id, Data data, Bitmap captcha);
	}

	public interface OnRosterUpdate {
		void onRosterUpdate();
	}

	public interface OnMucRosterUpdate {
		void onMucRosterUpdate();
	}

	public interface OnConferenceConfigurationFetched {
		void onConferenceConfigurationFetched(Conversation conversation);

		void onFetchFailed(Conversation conversation, Element error);
	}

	public interface OnConferenceJoined {
		void onConferenceJoined(Conversation conversation);
	}

	public interface OnConfigurationPushed {
		void onPushSucceeded();

		void onPushFailed();
	}

	public interface OnShowErrorToast {
		void onShowErrorToast(int resId);
	}

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}
}
