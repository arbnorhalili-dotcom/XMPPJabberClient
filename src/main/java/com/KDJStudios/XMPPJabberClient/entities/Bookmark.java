package com.KDJStudios.XMPPJabberClient.entities;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.KDJStudios.XMPPJabberClient.utils.UIHelper;
import com.KDJStudios.XMPPJabberClient.xml.Element;
import rocks.xmpp.addr.Jid;

public class Bookmark extends Element implements ListItem {

	private Account account;
	private WeakReference<Conversation> conversation;
	private Jid jid;

	public Bookmark(final Account account, final Jid jid) {
		super("conference");
		this.jid = jid;
		this.setAttribute("jid", jid.toString());
		this.account = account;
	}

	private Bookmark(Account account) {
		super("conference");
		this.account = account;
	}

	public static Bookmark parse(Element element, Account account) {
		Bookmark bookmark = new Bookmark(account);
		bookmark.setAttributes(element.getAttributes());
		bookmark.setChildren(element.getChildren());
		bookmark.jid =  bookmark.getAttributeAsJid("jid");
		return bookmark;
	}

	public void setAutojoin(boolean autojoin) {
		if (autojoin) {
			this.setAttribute("autojoin", "true");
		} else {
			this.setAttribute("autojoin", "false");
		}
	}

	@Override
	public int compareTo(final @NonNull ListItem another) {
		return this.getDisplayName().compareToIgnoreCase(
				another.getDisplayName());
	}

	@Override
	public String getDisplayName() {
		final Conversation c = getConversation();
		final String name = getBookmarkName();
		if (c != null) {
			return c.getName().toString();
		} else if (printableValue(name, false)) {
			return name.trim();
		} else {
			Jid jid = this.getJid();
			return jid != null && jid.getLocal() != null ? jid.getLocal() : "";
		}
	}

	public static boolean printableValue(@Nullable String value, boolean permitNone) {
		return value != null && !value.trim().isEmpty() && (permitNone || !"None".equals(value));
	}

	public static boolean printableValue(@Nullable String value) {
		return printableValue(value, true);
	}

	@Override
	public Jid getJid() {
		return this.jid;
	}

	@Override
	public List<Tag> getTags(Context context) {
		ArrayList<Tag> tags = new ArrayList<>();
		for (Element element : getChildren()) {
			if (element.getName().equals("group") && element.getContent() != null) {
				String group = element.getContent();
				tags.add(new Tag(group, UIHelper.getColorForName(group,true)));
			}
		}
		return tags;
	}

	public String getNick() {
		return this.findChildContent("nick");
	}

	public void setNick(String nick) {
		Element element = this.findChild("nick");
		if (element == null) {
			element = this.addChild("nick");
		}
		element.setContent(nick);
	}

	public boolean autojoin() {
		return this.getAttributeAsBoolean("autojoin");
	}

	public String getPassword() {
		return this.findChildContent("password");
	}

	public void setPassword(String password) {
		Element element = this.findChild("password");
		if (element != null) {
			element.setContent(password);
		}
	}

	@Override
	public boolean match(Context context, String needle) {
		if (needle == null) {
			return true;
		}
		needle = needle.toLowerCase(Locale.US);
		final Jid jid = getJid();
		return (jid != null && jid.toString().contains(needle)) ||
			getDisplayName().toLowerCase(Locale.US).contains(needle) ||
			matchInTag(context, needle);
	}

	private boolean matchInTag(Context context, String needle) {
		needle = needle.toLowerCase(Locale.US);
		for (Tag tag : getTags(context)) {
			if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	public Account getAccount() {
		return this.account;
	}

	public synchronized Conversation getConversation() {
		return this.conversation != null ? this.conversation.get() : null;
	}

	public synchronized void setConversation(Conversation conversation) {
		if (this.conversation != null) {
			this.conversation.clear();
		}
		this.conversation = new WeakReference<>(conversation);
	}

	public String getBookmarkName() {
		return this.getAttribute("name");
	}

	public boolean setBookmarkName(String name) {
		String before = getBookmarkName();
		if (name != null && !name.equals(before)) {
			this.setAttribute("name", name);
			return true;
		} else {
			return false;
		}
	}
}
