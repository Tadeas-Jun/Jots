package com.eledris.jots;

import java.util.Date;

public class Note implements Comparable<Note> {

    // ID in the format of jot_id[x], where x is a sequential number.
    public String id;

    public String title;
    public String content;
    public Date lastEdited;

    // Compare which note is older. Returns a number greater than 0 if this note is older than the compared note.
    @Override
    public int compareTo(Note comparingToNote) {

        Date comparingToDate = comparingToNote.lastEdited;
        return lastEdited.compareTo(comparingToDate);

    }

    // Are two notes the same in ID?
    @Override
    public boolean equals(Object o) {

        if(!(o instanceof Note)) return false;

        Note comparingToNote = (Note)o;

        return id.equals(comparingToNote.id);

    }

    // Are the note's content and title both empty?
    public boolean isEmpty() {

        boolean titleEmpty = ((title == null) || (title.trim().isEmpty()));
        boolean contentEmpty = ((content == null) || (content.trim().isEmpty()));

        return (titleEmpty && contentEmpty);

    }

}
