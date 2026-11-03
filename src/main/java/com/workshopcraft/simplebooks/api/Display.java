package com.workshopcraft.simplebooks.api;

/** What the book shows for a task: an item id for the icon (may be empty) and a lang key or literal text. */
public record Display(String icon, String text) {}
