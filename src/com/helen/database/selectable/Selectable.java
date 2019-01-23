package com.helen.database.selectable;

import java.io.IOException;

public interface Selectable {
	/** The name of the Selectable, shown in the "Did you mean:" section. */
	String getDisplay();

	/** Function called if selected. */
	String run() throws IOException;
}
