package com.helen.database.selectable;

import com.helen.database.Pages;

public class Author implements Selectable {
	
	public final String authorName;
	
	public Author(String author){
		this.authorName = author;
	}

	@Override
	public String getDisplay(){
		return this.authorName;
	}

	@Override
	public String run(){
		return Pages.getAuthorDetailsPages(this.authorName);
	}
}
