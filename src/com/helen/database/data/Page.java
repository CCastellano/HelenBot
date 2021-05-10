package com.helen.database.data;

import com.helen.database.Selectable;
import org.jibble.pircbot.Colors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.helen.database.data.Pages.findTime;

public class Page implements Selectable {


    private String pageLink;
    private String title;
    private Integer rating;
    private String createdBy;
    private java.sql.Timestamp createdAt;
    private Boolean scpPage;
    private String scpTitle;
    private List<Metadata> authorMetadata;
    private List<Metadata> dateMetadata;

    public Page(String pageLink, String title, Boolean scpPage, String scpTitle) {
        this.pageLink = pageLink;
        this.title = title;
        this.scpPage = scpPage;
        this.scpTitle = scpTitle;
    }

    public Page(String pageLink, String title, Integer rating, String createdBy,
                java.sql.Timestamp createdAt, Boolean scpPage, String scpTitle) {
        this.pageLink = pageLink;
        this.title = title;
        this.rating = rating;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.scpPage = scpPage;
        this.scpTitle = scpTitle;
    }

    public String getPageLink() {
        return pageLink;
    }

    public String getTitle() {
        return title;
    }

    public Integer getRating() {
        return rating;
    }

    public java.sql.Timestamp getCreatedAt() {
        return createdAt;
    }

    public Boolean getScpPage() {
        return scpPage;
    }

    public String getScpTitle() {
        return scpTitle;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setAuthorMetadata(List<Metadata> data){
        this.authorMetadata = data;
    }

    public void setDateMetadata(List<Metadata> data){
        this.dateMetadata = data;
    }
    
    public String getPageAsLine(){
        StringBuilder returnString = new StringBuilder();
        returnString.append(Colors.BOLD);

        if(this.getScpPage() && !this.getScpTitle().equals("[ACCESS DENIED]")){
            returnString.append(this.getTitle() + ": " + this.getScpTitle());
        }else{
            returnString.append(this.getTitle());
        }

        returnString.append(Colors.NORMAL);
        returnString.append(" (");
        returnString.append("Rating: ");
        returnString.append(this.getRating());
        returnString.append(". ");
        returnString.append("Written ");
        returnString.append(findTime(this.getCreatedAt().getTime()));
        returnString.append("By: ");
        Metadata finalDateMetadata = null;
        List<Metadata> finalDateMetadataList = new ArrayList<>();
        for(Metadata data : dateMetadata) {
            LocalDate newDate = LocalDate.parse(data.getDate());
            if (finalDateMetadata == null || LocalDate.parse(finalDateMetadata.getDate()).compareTo(newDate) < 0) {
                finalDateMetadataList.clear();
                finalDateMetadata = data;
                finalDateMetadataList.add(data);
            } else if (LocalDate.parse(finalDateMetadata.getDate()).compareTo(newDate) == 0) {
                finalDateMetadataList.add(data);
            }
        }

        if (finalDateMetadataList.isEmpty() && finalDateMetadata != null) {
            finalDateMetadataList.add(finalDateMetadata);
        }


        if (!authorMetadata.isEmpty()) {

            if (authorMetadata.size() == 1) {
                returnString.append(authorMetadata.get(0).getUsername());
            } else if (authorMetadata.size() == 2) {
                returnString.append(authorMetadata.get(0).getUsername()).append(" and ").append(authorMetadata.get(1).getUsername());
            } else {
                returnString.append(authorMetadata.stream().map(Metadata::getUsername).collect(Collectors.joining(", ")));
            }
        } else {
            returnString.append(this.getCreatedBy());
        }

        if (finalDateMetadata != null) {
            returnString.append(" rewritten on: ");
            returnString.append(finalDateMetadata.getDate());
            returnString.append(" by ");
            if (finalDateMetadataList.size() == 1) {
                returnString.append(finalDateMetadataList.get(0).getUsername());
            } else if (finalDateMetadataList.size() == 2) {
                returnString.append(finalDateMetadataList.get(0).getUsername()).append(" and ").append(finalDateMetadataList.get(1).getUsername());
            } else {
                returnString.append(finalDateMetadataList.stream().map(Metadata::getUsername).collect(Collectors.joining(", ")));
            }
        }

        returnString.append(")");
        returnString.append(" - ");
        returnString.append("http://scp-wiki.wikidot.com/");
        returnString.append(this.getPageLink());
        return returnString.toString();
    }

    @Override
    public Object selectResource() {
        return pageLink;
    }

    @Override
    public String toString() {
        return "Page{" +
                "pageLink='" + pageLink + '\'' +
                ", title='" + title + '\'' +
                ", rating=" + rating +
                ", createdBy='" + createdBy + '\'' +
                ", createdAt=" + createdAt +
                ", scpPage=" + scpPage +
                ", scpTitle='" + scpTitle + '\'' +
                '}';
    }

}
