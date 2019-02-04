package com.helen.database.selectable;

import javax.annotation.Nullable;

public interface Selectable {
	/** The name of the Selectable, shown in the "Did you mean:" section. */
	String getDisplay();

	/** Function called if selected. */
	@Nullable
	String run() throws Exception;
}
