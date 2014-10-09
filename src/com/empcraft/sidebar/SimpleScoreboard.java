package com.empcraft.sidebar;

import java.util.Collection;
import java.util.Map;

public class SimpleScoreboard
{
	private final String title;
	private final Map<String, String> scores;
	private final String permission;
	private final String description;
	public SimpleScoreboard(String title, Map<String, String> scores, String permission, String description)
	{
		this.title = title;
		this.scores = scores;
		this.permission = permission;
		this.description = description;
	}
	public final String getDescription()
	{
		return this.description;
	}
	public final String getPermission() {
		return this.permission;
	}
	public final Collection<String> getKeys() {
		return this.scores.keySet();
	}
	public final Collection<String> getValues() {
		return this.scores.values();
	}
	public final Map<String, String> getScores() {
		return this.scores;
	}
	public final String getTitle() {
		return this.title;
	}
}