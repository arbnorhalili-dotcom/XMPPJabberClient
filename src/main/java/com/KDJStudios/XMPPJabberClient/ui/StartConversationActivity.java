package com.KDJStudios.XMPPJabberClient.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.KDJStudios.XMPPJabberClient.Config;
import com.KDJStudios.XMPPJabberClient.R;
import com.KDJStudios.XMPPJabberClient.databinding.ActivityStartConversationBinding;
import com.KDJStudios.XMPPJabberClient.entities.Account;
import com.KDJStudios.XMPPJabberClient.entities.Bookmark;
import com.KDJStudios.XMPPJabberClient.entities.Contact;
import com.KDJStudios.XMPPJabberClient.entities.Conversation;
import com.KDJStudios.XMPPJabberClient.entities.ListItem;
import com.KDJStudios.XMPPJabberClient.entities.Presence;
import com.KDJStudios.XMPPJabberClient.services.XmppConnectionService;
import com.KDJStudios.XMPPJabberClient.services.XmppConnectionService.OnRosterUpdate;
import com.KDJStudios.XMPPJabberClient.ui.adapter.ListItemAdapter;
import com.KDJStudios.XMPPJabberClient.ui.interfaces.OnBackendConnected;
//import com.KDJStudios.XMPPJabberClient.ui.service.EmojiService;
import com.KDJStudios.XMPPJabberClient.ui.util.MenuDoubleTabUtil;
import com.KDJStudios.XMPPJabberClient.ui.util.PendingItem;
import com.KDJStudios.XMPPJabberClient.utils.XmppUri;
import com.KDJStudios.XMPPJabberClient.xmpp.OnUpdateBlocklist;
import com.KDJStudios.XMPPJabberClient.xmpp.XmppConnection;
import rocks.xmpp.addr.Jid;

public class StartConversationActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate, OnRosterUpdate, OnUpdateBlocklist, CreateConferenceDialog.CreateConferenceDialogListener, JoinConferenceDialog.JoinConferenceDialogListener {

	private final int REQUEST_SYNC_CONTACTS = 0x28cf;
	private final int REQUEST_CREATE_CONFERENCE = 0x39da;
	private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
	private final PendingItem<String> mInitialSearchValue = new PendingItem<>();
	private final AtomicBoolean oneShotKeyboardSuppress = new AtomicBoolean();
	public int conference_context_id;
	public int contact_context_id;
	private ListPagerAdapter mListPagerAdapter;
	private List<ListItem> contacts = new ArrayList<>();
	private ListItemAdapter mContactsAdapter;
	private List<ListItem> conferences = new ArrayList<>();
	private ListItemAdapter mConferenceAdapter;
	private List<String> mActivatedAccounts = new ArrayList<>();
	private EditText mSearchEditText;
	private AtomicBoolean mRequestedContactsPermission = new AtomicBoolean(false);
	private boolean mHideOfflineContacts = false;
	private MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {

		@Override
		public boolean onMenuItemActionExpand(MenuItem item) {
			mSearchEditText.post(() -> {
				mSearchEditText.requestFocus();
				if (oneShotKeyboardSuppress.compareAndSet(true, false)) {
					return;
				}
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				if (imm != null) {
					imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT);
				}
			});

			return true;
		}

		@Override
		public boolean onMenuItemActionCollapse(MenuItem item) {
			hideKeyboard();
			mSearchEditText.setText("");
			filter(null);
			return true;
		}
	};
	private TextWatcher mSearchTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable editable) {
			filter(editable.toString());
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
	};
	private MenuItem mMenuSearchView;
	private ListItemAdapter.OnTagClickedListener mOnTagClickedListener = new ListItemAdapter.OnTagClickedListener() {
		@Override
		public void onTagClicked(String tag) {
			if (mMenuSearchView != null) {
				mMenuSearchView.expandActionView();
				mSearchEditText.setText("");
				mSearchEditText.append(tag);
				filter(tag);
			}
		}
	};
	private Pair<Integer, Intent> mPostponedActivityResult;
	private Toast mToast;
	private UiCallback<Conversation> mAdhocConferenceCallback = new UiCallback<Conversation>() {
		@Override
		public void success(final Conversation conversation) {
			runOnUiThread(() -> {
				hideToast();
				switchToConversation(conversation);
			});
		}

		@Override
		public void error(final int errorCode, Conversation object) {
			runOnUiThread(() -> replaceToast(getString(errorCode)));
		}

		@Override
		public void userInputRequried(PendingIntent pi, Conversation object) {

		}
	};
	private ActivityStartConversationBinding binding;
	private TextView.OnEditorActionListener mSearchDone = new TextView.OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			int pos = binding.startConversationViewPager.getCurrentItem();
			if (pos == 0) {
				if (contacts.size() == 1) {
					openConversationForContact((Contact) contacts.get(0));
					return true;
				}
			} else {
				if (conferences.size() == 1) {
					openConversationsForBookmark((Bookmark) conferences.get(0));
					return true;
				}
			}
			hideKeyboard();
			mListPagerAdapter.requestFocus(pos);
			return true;
		}
	};
	private ViewPager.SimpleOnPageChangeListener mOnPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
		@Override
		public void onPageSelected(int position) {
			onTabChanged();
		}
	};

	public static void populateAccountSpinner(Context context, List<String> accounts, Spinner spinner) {
		if (accounts.size() > 0) {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.simple_list_item, accounts);
			adapter.setDropDownViewResource(R.layout.simple_list_item);
			spinner.setAdapter(adapter);
			spinner.setEnabled(true);
		} else {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
					R.layout.simple_list_item,
					Arrays.asList(context.getString(R.string.no_accounts)));
			adapter.setDropDownViewResource(R.layout.simple_list_item);
			spinner.setAdapter(adapter);
			spinner.setEnabled(false);
		}
	}

	public static void launch(Context context) {
		final Intent intent = new Intent(context, StartConversationActivity.class);
		context.startActivity(intent);
	}

	private static Intent createLauncherIntent(Context context) {
		final Intent intent = new Intent(context, StartConversationActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		return intent;
	}

	private static boolean isViewIntent(final Intent i) {
		return i != null && (Intent.ACTION_VIEW.equals(i.getAction()) || Intent.ACTION_SENDTO.equals(i.getAction()) || i.hasExtra(WelcomeActivity.EXTRA_INVITE_URI));
	}

	protected void hideToast() {
		if (mToast != null) {
			mToast.cancel();
		}
	}

	protected void replaceToast(String msg) {
		hideToast();
		mToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		mToast.show();
	}

	@Override
	public void onRosterUpdate() {
		this.refreshUi();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//new EmojiService(this).init();
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_start_conversation);
		Toolbar toolbar = (Toolbar) binding.toolbar;
		setSupportActionBar(toolbar);
		configureActionBar(getSupportActionBar());
		this.binding.fab.setOnClickListener((v) -> {
			if (binding.startConversationViewPager.getCurrentItem() == 0) {
				String searchString = mSearchEditText != null ? mSearchEditText.getText().toString() : null;
				if (searchString != null && !searchString.trim().isEmpty()) {
					try {
						Jid jid = Jid.of(searchString);
						if (jid.getLocal() != null && jid.isBareJid() && jid.getDomain().contains(".")) {
							showCreateContactDialog(jid.toString(), null);
							return;
						}
					} catch (IllegalArgumentException ignored) {
						//ignore and fall through
					}
				}
				showCreateContactDialog(null, null);
			} else {
				showCreateConferenceDialog();
			}
		});
		binding.tabLayout.setupWithViewPager(binding.startConversationViewPager);
		binding.startConversationViewPager.addOnPageChangeListener(mOnPageChangeListener);
		mListPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
		binding.startConversationViewPager.setAdapter(mListPagerAdapter);

		mConferenceAdapter = new ListItemAdapter(this, conferences);
		mContactsAdapter = new ListItemAdapter(this, contacts);
		mContactsAdapter.setOnTagClickedListener(this.mOnTagClickedListener);
		this.mHideOfflineContacts = getPreferences().getBoolean("hide_offline", false);

		final Intent intent;
		if (savedInstanceState == null) {
			intent = getIntent();
		} else {
			final String search = savedInstanceState.getString("search");
			if (search != null) {
				mInitialSearchValue.push(search);
			}
			intent = savedInstanceState.getParcelable("intent");
		}

		if (isViewIntent(intent)) {
			pendingViewIntent.push(intent);
			setIntent(createLauncherIntent(this));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		Intent pendingIntent = pendingViewIntent.peek();
		savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
		if (mMenuSearchView != null && mMenuSearchView.isActionViewExpanded()) {
			savedInstanceState.putString("search", mSearchEditText != null ? mSearchEditText.getText().toString() : null);
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onStart() {
		super.onStart();
		final int theme = findTheme();
		if (this.mTheme != theme) {
			recreate();
		} else {
			if (pendingViewIntent.peek() == null) {
				askForContactsPermissions();
			}
		}
		mConferenceAdapter.refreshSettings();
		mContactsAdapter.refreshSettings();
	}

	@Override
	public void onNewIntent(final Intent intent) {
		if (xmppConnectionServiceBound) {
			processViewIntent(intent);
		} else {
			pendingViewIntent.push(intent);
		}
		setIntent(createLauncherIntent(this));
	}

	protected void openConversationForContact(int position) {
		Contact contact = (Contact) contacts.get(position);
		openConversationForContact(contact);
	}

	protected void openConversationForContact(Contact contact) {
		Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false, true);
		switchToConversation(conversation);
	}

	protected void openConversationForContact() {
		int position = contact_context_id;
		openConversationForContact(position);
	}

	protected void openConversationForBookmark() {
		openConversationForBookmark(conference_context_id);
	}

	protected void openConversationForBookmark(int position) {
		Bookmark bookmark = (Bookmark) conferences.get(position);
		openConversationsForBookmark(bookmark);
	}

	protected void shareBookmarkUri() {
		shareBookmarkUri(conference_context_id);
	}

	protected void shareBookmarkUri(int position) {
		Bookmark bookmark = (Bookmark) conferences.get(position);
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		shareIntent.putExtra(Intent.EXTRA_TEXT, "xmpp:" + bookmark.getJid().asBareJid().toEscapedString() + "?join");
		shareIntent.setType("text/plain");
		try {
			startActivity(Intent.createChooser(shareIntent, getText(R.string.share_uri_with)));
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
		}
	}

	protected void openConversationsForBookmark(Bookmark bookmark) {
		Jid jid = bookmark.getJid();
		if (jid == null) {
			Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
			return;
		}
		Conversation conversation = xmppConnectionService.findOrCreateConversation(bookmark.getAccount(), jid, true, true, true);
		bookmark.setConversation(conversation);
		if (!bookmark.autojoin() && getPreferences().getBoolean("autojoin", getResources().getBoolean(R.bool.autojoin))) {
			bookmark.setAutojoin(true);
			xmppConnectionService.pushBookmarks(bookmark.getAccount());
		}
		switchToConversation(conversation);
	}

	protected void openDetailsForContact() {
		int position = contact_context_id;
		Contact contact = (Contact) contacts.get(position);
		switchToContactDetails(contact);
	}

	protected void toggleContactBlock() {
		final int position = contact_context_id;
		BlockContactDialog.show(this, (Contact) contacts.get(position));
	}

	protected void deleteContact() {
		final int position = contact_context_id;
		final Contact contact = (Contact) contacts.get(position);
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setTitle(R.string.action_delete_contact);
		builder.setMessage(getString(R.string.remove_contact_text, contact.getJid()));
		builder.setPositiveButton(R.string.delete, (dialog, which) -> {
			xmppConnectionService.deleteContactOnServer(contact);
			filter(mSearchEditText.getText().toString());
		});
		builder.create().show();
	}

	protected void deleteConference() {
		int position = conference_context_id;
		final Bookmark bookmark = (Bookmark) conferences.get(position);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setTitle(R.string.delete_bookmark);
		builder.setMessage(getString(R.string.remove_bookmark_text,
				bookmark.getJid()));
		builder.setPositiveButton(R.string.delete, (dialog, which) -> {
			bookmark.setConversation(null);
			Account account = bookmark.getAccount();
			account.getBookmarks().remove(bookmark);
			xmppConnectionService.pushBookmarks(account);
			filter(mSearchEditText.getText().toString());
		});
		builder.create().show();

	}

	@SuppressLint("InflateParams")
	protected void showCreateContactDialog(final String prefilledJid, final Invite invite) {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		EnterJidDialog dialog = EnterJidDialog.newInstance(
				mActivatedAccounts,
				getString(R.string.dialog_title_create_contact),
				getString(R.string.create),
				prefilledJid,
				null,
				invite == null || !invite.hasFingerprints()
		);

		dialog.setOnEnterJidDialogPositiveListener((accountJid, contactJid) -> {
			if (!xmppConnectionServiceBound) {
				return false;
			}

			final Account account = xmppConnectionService.findAccountByJid(accountJid);
			if (account == null) {
				return true;
			}

			final Contact contact = account.getRoster().getContact(contactJid);
			if (invite != null && invite.getName() != null) {
				contact.setServerName(invite.getName());
			}
			if (contact.isSelf()) {
				switchToConversation(contact, null);
				return true;
			} else if (contact.showInRoster()) {
				throw new EnterJidDialog.JidError(getString(R.string.contact_already_exists));
			} else {
				xmppConnectionService.createContact(contact, true);
				if (invite != null && invite.hasFingerprints()) {
					xmppConnectionService.verifyFingerprints(contact, invite.getFingerprints());
				}
				switchToConversation(contact, invite == null ? null : invite.getBody());
				return true;
			}
		});
		dialog.show(ft, FRAGMENT_TAG_DIALOG);
	}

	@SuppressLint("InflateParams")
	protected void showJoinConferenceDialog(final String prefilledJid) {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		JoinConferenceDialog joinConferenceFragment = JoinConferenceDialog.newInstance(prefilledJid, mActivatedAccounts);
		joinConferenceFragment.show(ft, FRAGMENT_TAG_DIALOG);
	}

	private void showCreateConferenceDialog() {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		CreateConferenceDialog createConferenceFragment = CreateConferenceDialog.newInstance(mActivatedAccounts);
		createConferenceFragment.show(ft, FRAGMENT_TAG_DIALOG);
	}

	private Account getSelectedAccount(Spinner spinner) {
		if (!spinner.isEnabled()) {
			return null;
		}
		Jid jid;
		try {
			if (Config.DOMAIN_LOCK != null) {
				jid = Jid.of((String) spinner.getSelectedItem(), Config.DOMAIN_LOCK, null);
			} else {
				jid = Jid.of((String) spinner.getSelectedItem());
			}
		} catch (final IllegalArgumentException e) {
			return null;
		}
		return xmppConnectionService.findAccountByJid(jid);
	}

	protected void switchToConversation(Contact contact, String body) {
		Conversation conversation = xmppConnectionService
				.findOrCreateConversation(contact.getAccount(),
						contact.getJid(), false, true);
		switchToConversation(conversation, body, false);
	}

	@Override
	public void invalidateOptionsMenu() {
		boolean isExpanded = mMenuSearchView != null && mMenuSearchView.isActionViewExpanded();
		String text = mSearchEditText != null ? mSearchEditText.getText().toString() : "";
		if (isExpanded) {
			mInitialSearchValue.push(text);
			oneShotKeyboardSuppress.set(true);
		}
		super.invalidateOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.start_conversation, menu);
		MenuItem menuHideOffline = menu.findItem(R.id.action_hide_offline);
		MenuItem joinGroupChat = menu.findItem(R.id.action_join_conference);
		MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
		joinGroupChat.setVisible(binding.startConversationViewPager.getCurrentItem() == 1);
		qrCodeScanMenuItem.setVisible(isCameraFeatureAvailable());
		menuHideOffline.setChecked(this.mHideOfflineContacts);
		mMenuSearchView = menu.findItem(R.id.action_search);
		mMenuSearchView.setOnActionExpandListener(mOnActionExpandListener);
		View mSearchView = mMenuSearchView.getActionView();
		mSearchEditText = mSearchView.findViewById(R.id.search_field);
		mSearchEditText.addTextChangedListener(mSearchTextWatcher);
		mSearchEditText.setOnEditorActionListener(mSearchDone);
		String initialSearchValue = mInitialSearchValue.pop();
		if (initialSearchValue != null) {
			mMenuSearchView.expandActionView();
			mSearchEditText.append(initialSearchValue);
			filter(initialSearchValue);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (MenuDoubleTabUtil.shouldIgnoreTap()) {
			return false;
		}
		switch (item.getItemId()) {
			case android.R.id.home:
				navigateBack();
				return true;
			case R.id.action_join_conference:
				showJoinConferenceDialog(null);
				return true;
			case R.id.action_scan_qr_code:
				UriHandlerActivity.scan(this);
				return true;
			case R.id.action_hide_offline:
				mHideOfflineContacts = !item.isChecked();
				getPreferences().edit().putBoolean("hide_offline", mHideOfflineContacts).commit();
				if (mSearchEditText != null) {
					filter(mSearchEditText.getText().toString());
				}
				invalidateOptionsMenu();
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH && !event.isLongPress()) {
			openSearch();
			return true;
		}
		int c = event.getUnicodeChar();
		if (c > 32) {
			if (mSearchEditText != null && !mSearchEditText.isFocused()) {
				openSearch();
				mSearchEditText.append(Character.toString((char) c));
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	private void openSearch() {
		if (mMenuSearchView != null) {
			mMenuSearchView.expandActionView();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK) {
			if (xmppConnectionServiceBound) {
				this.mPostponedActivityResult = null;
				if (requestCode == REQUEST_CREATE_CONFERENCE) {
					Account account = extractAccount(intent);
					final String subject = intent.getStringExtra("subject");
					List<Jid> jids = new ArrayList<>();
					if (intent.getBooleanExtra("multiple", false)) {
						String[] toAdd = intent.getStringArrayExtra("contacts");
						for (String item : toAdd) {
							try {
								jids.add(Jid.of(item));
							} catch (IllegalArgumentException e) {
								//ignored
							}
						}
					} else {
						try {
							jids.add(Jid.of(intent.getStringExtra("contact")));
						} catch (Exception e) {
							//ignored
						}
					}
					if (account != null && jids.size() > 0) {
						if (xmppConnectionService.createAdhocConference(account, subject, jids, mAdhocConferenceCallback)) {
							mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
							mToast.show();
						}
					}
				}
			} else {
				this.mPostponedActivityResult = new Pair<>(requestCode, intent);
			}
		}
		super.onActivityResult(requestCode, requestCode, intent);
	}

	private void askForContactsPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
				if (mRequestedContactsPermission.compareAndSet(false, true)) {
					if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
						AlertDialog.Builder builder = new AlertDialog.Builder(this);
						builder.setTitle(R.string.sync_with_contacts);
						builder.setMessage(R.string.sync_with_contacts_long);
						builder.setPositiveButton(R.string.next, (dialog, which) -> {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
								requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
							}
						});
						builder.setOnDismissListener(dialog -> {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
								requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
							}
						});
						builder.create().show();
					} else {
						requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 0);
					}
				}
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				ScanActivity.onRequestPermissionResult(this, requestCode, grantResults);
				if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
					xmppConnectionService.loadPhoneContacts();
				}
			}
	}

	private void configureHomeButton() {
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			return;
		}
		boolean openConversations = !xmppConnectionService.isConversationsListEmpty(null);
		actionBar.setDisplayHomeAsUpEnabled(openConversations);
		actionBar.setDisplayHomeAsUpEnabled(openConversations);

	}

	@Override
	protected void onBackendConnected() {
		if (mPostponedActivityResult != null) {
			onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
			this.mPostponedActivityResult = null;
		}
		this.mActivatedAccounts.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED) {
				if (Config.DOMAIN_LOCK != null) {
					this.mActivatedAccounts.add(account.getJid().getLocal());
				} else {
					this.mActivatedAccounts.add(account.getJid().asBareJid().toString());
				}
			}
		}
		configureHomeButton();
		Intent intent = pendingViewIntent.pop();
		if (intent != null && processViewIntent(intent)) {
			filter(null);
		} else {
			if (mSearchEditText != null) {
				filter(mSearchEditText.getText().toString());
			} else {
				filter(null);
			}
		}
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (fragment != null && fragment instanceof OnBackendConnected) {
			Log.d(Config.LOGTAG, "calling on backend connected on dialog");
			((OnBackendConnected) fragment).onBackendConnected();
		}
	}

	protected boolean processViewIntent(@NonNull Intent intent) {
		final String inviteUri = intent.getStringExtra(WelcomeActivity.EXTRA_INVITE_URI);
		if (inviteUri != null) {
			Invite invite = new Invite(inviteUri);
			if (invite.isJidValid()) {
				return invite.invite();
			}
		}
		switch (intent.getAction()) {
			case Intent.ACTION_SENDTO:
			case Intent.ACTION_VIEW:
				Uri uri = intent.getData();
				if (uri != null) {
					Invite invite = new Invite(intent.getData(), intent.getBooleanExtra("scanned", false));
					invite.account = intent.getStringExtra("account");
					return invite.invite();
				} else {
					return false;
				}
		}
		return false;
	}

	private boolean handleJid(Invite invite) {
		List<Contact> contacts = xmppConnectionService.findContacts(invite.getJid(), invite.account);
		if (invite.isAction(XmppUri.ACTION_JOIN)) {
			Conversation muc = xmppConnectionService.findFirstMuc(invite.getJid());
			if (muc != null) {
				switchToConversation(muc, invite.getBody(), false);
				return true;
			} else {
				showJoinConferenceDialog(invite.getJid().asBareJid().toString());
				return false;
			}
		} else if (contacts.size() == 0) {
			showCreateContactDialog(invite.getJid().toString(), invite);
			return false;
		} else if (contacts.size() == 1) {
			Contact contact = contacts.get(0);
			if (!invite.isSafeSource() && invite.hasFingerprints()) {
				displayVerificationWarningDialog(contact, invite);
			} else {
				if (invite.hasFingerprints()) {
					if (xmppConnectionService.verifyFingerprints(contact, invite.getFingerprints())) {
						Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
					}
				}
				if (invite.account != null) {
					xmppConnectionService.getShortcutService().report(contact);
				}
				switchToConversation(contact, invite.getBody());
			}
			return true;
		} else {
			if (mMenuSearchView != null) {
				mMenuSearchView.expandActionView();
				mSearchEditText.setText("");
				mSearchEditText.append(invite.getJid().toString());
				filter(invite.getJid().toString());
			} else {
				mInitialSearchValue.push(invite.getJid().toString());
			}
			return true;
		}
	}

	private void displayVerificationWarningDialog(final Contact contact, final Invite invite) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.verify_omemo_keys);
		View view = getLayoutInflater().inflate(R.layout.dialog_verify_fingerprints, null);
		final CheckBox isTrustedSource = view.findViewById(R.id.trusted_source);
		TextView warning = view.findViewById(R.id.warning);
		String jid = contact.getJid().asBareJid().toString();
		SpannableString spannable = new SpannableString(getString(R.string.verifying_omemo_keys_trusted_source, jid, contact.getDisplayName()));
		int start = spannable.toString().indexOf(jid);
		if (start >= 0) {
			spannable.setSpan(new TypefaceSpan("monospace"), start, start + jid.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		warning.setText(spannable);
		builder.setView(view);
		builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
			if (isTrustedSource.isChecked() && invite.hasFingerprints()) {
				xmppConnectionService.verifyFingerprints(contact, invite.getFingerprints());
			}
			switchToConversation(contact, invite.getBody());
		});
		builder.setNegativeButton(R.string.cancel, (dialog, which) -> StartConversationActivity.this.finish());
		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.setOnCancelListener(dialog1 -> StartConversationActivity.this.finish());
		dialog.show();
	}

	protected void filter(String needle) {
		if (xmppConnectionServiceBound) {
			this.filterContacts(needle);
			this.filterConferences(needle);
		}
	}

	protected void filterContacts(String needle) {
		this.contacts.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED) {
				for (Contact contact : account.getRoster().getContacts()) {
					Presence.Status s = contact.getShownStatus();
					if (contact.showInRoster() && contact.match(this, needle)
							&& (!this.mHideOfflineContacts
							|| (needle != null && !needle.trim().isEmpty())
							|| s.compareTo(Presence.Status.OFFLINE) < 0)) {
						this.contacts.add(contact);
					}
				}
			}
		}
		Collections.sort(this.contacts);
		mContactsAdapter.notifyDataSetChanged();
	}

	protected void filterConferences(String needle) {
		this.conferences.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED) {
				for (Bookmark bookmark : account.getBookmarks()) {
					if (bookmark.match(this, needle)) {
						this.conferences.add(bookmark);
					}
				}
			}
		}
		Collections.sort(this.conferences);
		mConferenceAdapter.notifyDataSetChanged();
	}

	private void onTabChanged() {
		@DrawableRes final int fabDrawable;
		if (binding.startConversationViewPager.getCurrentItem() == 0) {
			fabDrawable = R.drawable.ic_person_add_white_24dp;
		} else {
			fabDrawable = R.drawable.ic_group_add_white_24dp;
		}
		binding.fab.setImageResource(fabDrawable);
		invalidateOptionsMenu();
	}

	@Override
	public void OnUpdateBlocklist(final Status status) {
		refreshUi();
	}

	@Override
	protected void refreshUiReal() {
		if (mSearchEditText != null) {
			filter(mSearchEditText.getText().toString());
		}
		configureHomeButton();
	}

	@Override
	public void onBackPressed() {
		navigateBack();
	}

	private void navigateBack() {
		if (xmppConnectionService != null && !xmppConnectionService.isConversationsListEmpty(null)) {
			Intent intent = new Intent(this, ConversationsActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
		finish();
	}

	@Override
	public void onCreateDialogPositiveClick(Spinner spinner, String subject) {
		if (!xmppConnectionServiceBound) {
			return;
		}
		final Account account = getSelectedAccount(spinner);
		if (account == null) {
			return;
		}
		Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
		intent.putExtra("multiple", true);
		intent.putExtra("show_enter_jid", true);
		intent.putExtra("subject", subject);
		intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toString());
		intent.putExtra(ChooseContactActivity.EXTRA_TITLE_RES_ID, R.string.choose_participants);
		startActivityForResult(intent, REQUEST_CREATE_CONFERENCE);
	}

	@Override
	public void onJoinDialogPositiveClick(Dialog dialog, Spinner spinner, AutoCompleteTextView jid, boolean isBookmarkChecked) {
		if (!xmppConnectionServiceBound) {
			return;
		}
		final Account account = getSelectedAccount(spinner);
		if (account == null) {
			return;
		}
		final Jid conferenceJid;
		try {
			conferenceJid = Jid.of(jid.getText().toString());
		} catch (final IllegalArgumentException e) {
			jid.setError(getString(R.string.invalid_jid));
			return;
		}

		if (isBookmarkChecked) {
			if (account.hasBookmarkFor(conferenceJid)) {
				jid.setError(getString(R.string.bookmark_already_exists));
			} else {
				final Bookmark bookmark = new Bookmark(account, conferenceJid.asBareJid());
				bookmark.setAutojoin(getPreferences().getBoolean("autojoin", getResources().getBoolean(R.bool.autojoin)));
				String nick = conferenceJid.getResource();
				if (nick != null && !nick.isEmpty()) {
					bookmark.setNick(nick);
				}
				account.getBookmarks().add(bookmark);
				xmppConnectionService.pushBookmarks(account);
				final Conversation conversation = xmppConnectionService
						.findOrCreateConversation(account, conferenceJid, true, true, true);
				bookmark.setConversation(conversation);
				dialog.dismiss();
				switchToConversation(conversation);
			}
		} else {
			final Conversation conversation = xmppConnectionService
					.findOrCreateConversation(account, conferenceJid, true, true, true);
			dialog.dismiss();
			switchToConversation(conversation);
		}
	}

	@Override
	public void onConversationUpdate() {
		refreshUi();
	}

	public static class MyListFragment extends ListFragment {
		private AdapterView.OnItemClickListener mOnItemClickListener;
		private int mResContextMenu;

		public void setContextMenu(final int res) {
			this.mResContextMenu = res;
		}

		@Override
		public void onListItemClick(final ListView l, final View v, final int position, final long id) {
			if (mOnItemClickListener != null) {
				mOnItemClickListener.onItemClick(l, v, position, id);
			}
		}

		public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
			this.mOnItemClickListener = l;
		}

		@Override
		public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
			super.onViewCreated(view, savedInstanceState);
			registerForContextMenu(getListView());
			getListView().setFastScrollEnabled(true);
			getListView().setDivider(null);
			getListView().setDividerHeight(0);
		}

		@Override
		public void onCreateContextMenu(final ContextMenu menu, final View v,
		                                final ContextMenuInfo menuInfo) {
			super.onCreateContextMenu(menu, v, menuInfo);
			final StartConversationActivity activity = (StartConversationActivity) getActivity();
			if (activity == null) {
				return;
			}
			activity.getMenuInflater().inflate(mResContextMenu, menu);
			final AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
			if (mResContextMenu == R.menu.conference_context) {
				activity.conference_context_id = acmi.position;
			} else if (mResContextMenu == R.menu.contact_context) {
				activity.contact_context_id = acmi.position;
				final Contact contact = (Contact) activity.contacts.get(acmi.position);
				final MenuItem blockUnblockItem = menu.findItem(R.id.context_contact_block_unblock);
				final MenuItem showContactDetailsItem = menu.findItem(R.id.context_contact_details);
				if (contact.isSelf()) {
					showContactDetailsItem.setVisible(false);
				}
				XmppConnection xmpp = contact.getAccount().getXmppConnection();
				if (xmpp != null && xmpp.getFeatures().blocking() && !contact.isSelf()) {
					if (contact.isBlocked()) {
						blockUnblockItem.setTitle(R.string.unblock_contact);
					} else {
						blockUnblockItem.setTitle(R.string.block_contact);
					}
				} else {
					blockUnblockItem.setVisible(false);
				}
			}
		}

		@Override
		public boolean onContextItemSelected(final MenuItem item) {
			StartConversationActivity activity = (StartConversationActivity) getActivity();
			if (activity == null) {
				return true;
			}
			switch (item.getItemId()) {
				case R.id.context_start_conversation:
					activity.openConversationForContact();
					break;
				case R.id.context_contact_details:
					activity.openDetailsForContact();
					break;
				case R.id.context_contact_block_unblock:
					activity.toggleContactBlock();
					break;
				case R.id.context_delete_contact:
					activity.deleteContact();
					break;
				case R.id.context_join_conference:
					activity.openConversationForBookmark();
					break;
				case R.id.context_share_uri:
					activity.shareBookmarkUri();
					break;
				case R.id.context_delete_conference:
					activity.deleteConference();
			}
			return true;
		}
	}

	public class ListPagerAdapter extends PagerAdapter {
		FragmentManager fragmentManager;
		MyListFragment[] fragments;

		public ListPagerAdapter(FragmentManager fm) {
			fragmentManager = fm;
			fragments = new MyListFragment[2];
		}

		public void requestFocus(int pos) {
			if (fragments.length > pos) {
				fragments[pos].getListView().requestFocus();
			}
		}

		@Override
		public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
			assert (0 <= position && position < fragments.length);
			FragmentTransaction trans = fragmentManager.beginTransaction();
			trans.remove(fragments[position]);
			trans.commit();
			fragments[position] = null;
		}

		@Override
		public Fragment instantiateItem(@NonNull ViewGroup container, int position) {
			Fragment fragment = getItem(position);
			FragmentTransaction trans = fragmentManager.beginTransaction();
			trans.add(container.getId(), fragment, "fragment:" + position);
			trans.commit();
			return fragment;
		}

		@Override
		public int getCount() {
			return fragments.length;
		}

		@Override
		public boolean isViewFromObject(@NonNull View view, @NonNull Object fragment) {
			return ((Fragment) fragment).getView() == view;
		}

		@Nullable
		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
				case 0:
					return getResources().getString(R.string.contacts);
				case 1:
					return getResources().getString(R.string.conferences);
				default:
					return super.getPageTitle(position);
			}
		}

		public Fragment getItem(int position) {
			assert (0 <= position && position < fragments.length);
			if (fragments[position] == null) {
				final MyListFragment listFragment = new MyListFragment();
				if (position == 1) {
					listFragment.setListAdapter(mConferenceAdapter);
					listFragment.setContextMenu(R.menu.conference_context);
					listFragment.setOnListItemClickListener((arg0, arg1, p, arg3) -> openConversationForBookmark(p));
				} else {

					listFragment.setListAdapter(mContactsAdapter);
					listFragment.setContextMenu(R.menu.contact_context);
					listFragment.setOnListItemClickListener((arg0, arg1, p, arg3) -> openConversationForContact(p));
				}
				fragments[position] = listFragment;
			}
			return fragments[position];
		}
	}

	private class Invite extends XmppUri {

		public String account;

		public Invite(final Uri uri) {
			super(uri);
		}

		public Invite(final String uri) {
			super(uri);
		}

		public Invite(Uri uri, boolean safeSource) {
			super(uri, safeSource);
		}

		boolean invite() {
			if (!isJidValid()) {
				Toast.makeText(StartConversationActivity.this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
				return false;
			}
			if (getJid() != null) {
				return handleJid(this);
			}
			return false;
		}
	}
}
