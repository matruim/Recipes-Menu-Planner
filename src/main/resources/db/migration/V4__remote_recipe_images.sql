-- Some sites serve their photos through a CDN that refuses this server while
-- happily serving the browser looking at the page. Those are linked rather than
-- copied, so a picture still shows.
ALTER TABLE recipes ADD COLUMN image_url TEXT;
