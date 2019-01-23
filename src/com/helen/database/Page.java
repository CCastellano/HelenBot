package com.helen.database;

import java.util.ArrayList;
import java.util.Arrays;

public class Page implements Selectable {

	final String pageLink;
	final String title;
	final int rating;
	final String createdBy;
	final java.sql.Timestamp createdAt;

	final boolean scpPage;
	final String scpTitle;

	private ArrayList<Tag> tags;

	public Page (String pageLink, String title, boolean scpPage, String scpTitle){
		this.pageLink = pageLink;
		this.title = title;
		this.scpPage = scpPage;
		this.scpTitle = scpTitle;
		this.rating = 0;
		this.createdBy = null;
		this.createdAt = null;
	}

	public Page (String pageLink, String title, Integer rating, String createdBy,
							 java.sql.Timestamp createdAt, Boolean scpPage, String scpTitle){
		this.pageLink = pageLink;
		this.title = title;
		this.rating = rating;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.scpPage = scpPage;
		this.scpTitle = scpTitle;
	}

	@Override
	public Object selectResource() {
		return pageLink;
	}

	@Override
	public int hashCode() {
		return this.pageLink.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Page && ((Page) o).pageLink.equals(this.pageLink);
	}
}
