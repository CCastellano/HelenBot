package com.helen.commands;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface IRCCommand {
	String[] command();
	boolean startOfLine();
	boolean reg() default false;
	String[] regex() default "";
	int matcherGroup () default -1;
	boolean coexistWithJarvis() default false;
	int securityLevel();
	// boolean requiresToggle() default false;
}
