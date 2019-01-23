package com.helen.database;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;

public class Page implements Selectable {

	final String pageLink;
	final String title;
	final int rating;
	final String createdBy;
	final java.sql.Timestamp createdAt;

	public Page(String pageLink, String title, String scpTitle){
		this.pageLink = pageLink;
		this.title = title(title, scpTitle);
		this.rating = 0;
		this.createdBy = null;
		this.createdAt = null;
	}

	public Page(String pageLink, String title, Integer rating, String createdBy,
							Timestamp createdAt, String scpTitle){
		this.pageLink = pageLink;
		this.title = title(title, scpTitle);
		this.rating = rating;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	private String title(String title, String scpTitle) {
		return scpTitle == null ? title : scpTitle + ": " + title;
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
