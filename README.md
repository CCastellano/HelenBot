# HelenBot
<http://home.helenbot.com>

## Installation

1. Clone the repository.
2. Set up a [PostgreSQL database](https://www.postgresql.org/download/).
3. Run `scripts/init.sql`.
4. Copy `resources/.env.example` to `resources/.env` and fill out your database information.

## Usage

**[offline_main](/src/com/irc/helen/offline_main)** runs an offline version of the bot in the console, which is convenient for testing changes.

**[full_main](/src/com/irc/helen/full_main)** runs the bot and all its background processes asynchronously on timers. This is the recommended main program.

**[helen_main](/src/com/irc/helen/helen_main)** runs the bot on its own without gathering any information from the wiki.

**[background_main](/src/com/irc/helen/background_main)** runs all background processes once. It can be useful if invoked on an external timer, but is redundant with full_main.

## Configuration

Properties are stored in environment variables. A [.env](#/resources/.env.example) file can be used for development purposes, but [production instances should rely solely on env vars](https://github.com/cdimascio/java-dotenv#faq). If you want to be able to change a property without restarting the program, you can leave it undefined in env vars and add it to the `properties` table, but be careful: if a property is undefined in env vars, deleting it from the `properties` table may cause the program to crash.

Timer durations are stored in the database's `timers` table.

If you want to override the body of a database query used in the code, add an entry in the `statements` table with the name of the query and your replacement text.