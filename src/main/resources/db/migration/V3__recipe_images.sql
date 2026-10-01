-- Recipes get a picture: either imported from the source page or uploaded.
-- Only the file name is stored; the directory holding them is configurable.
ALTER TABLE recipes ADD COLUMN image_file TEXT;
