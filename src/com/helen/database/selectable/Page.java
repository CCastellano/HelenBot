package com.helen.database.selectable;

import com.helen.database.Pages;

import java.sql.Timestamp;

public class Page implements Selectable {

	public final String pageLink;
	public final String title;
	public final int rating;
	public final String createdBy;
	public final java.sql.Timestamp createdAt;

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
	public String getDisplay() {
		return this.title;
	}

	@Override
	public String run() {
		return Pages.getPageInfo(this.pageLink);
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
