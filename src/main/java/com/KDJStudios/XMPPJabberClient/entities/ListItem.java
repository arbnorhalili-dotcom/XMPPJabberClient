package com.KDJStudios.XMPPJabberClient.entities;

import android.content.Context;

import java.util.List;

import rocks.xmpp.addr.Jid;


public interface ListItem extends Comparable<ListItem> {
	String getDisplayName();

	Jid getJid();

	List<Tag> getTags(Context context);

	final class Tag {
		private final String name;
		private final int color;

		public Tag(final String name, final int color) {
			this.name = name;
			this.color = color;
		}

		public int getColor() {
			return this.color;
		}

		public String getName() {
			return this.name;
		}
	}

	boolean match(Context context, final String needle);
}
