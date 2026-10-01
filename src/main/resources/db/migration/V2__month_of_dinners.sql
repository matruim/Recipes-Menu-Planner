-- Planning moves from a week of breakfast/lunch/dinner slots to a month calendar
-- of dinners. A whole month can be laid out in one sitting; weeks survive only
-- as the window a shopping list is built over.

CREATE TABLE planned_meals (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_date DATE    NOT NULL,
    recipe_id UUID    NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    servings  INTEGER NOT NULL CHECK (servings > 0),
    position  INTEGER NOT NULL DEFAULT 0,
    -- The same recipe twice on one night would just be one bigger portion.
    UNIQUE (meal_date, recipe_id)
);

CREATE INDEX planned_meals_date_idx ON planned_meals (meal_date);

-- Carry existing entries across. Slots collapse into the date, so a recipe that
-- was planned for both lunch and dinner on one day becomes a single dinner;
-- the dinner row wins where they disagree on servings.
INSERT INTO planned_meals (meal_date, recipe_id, servings)
SELECT DISTINCT ON (p.week_start + e.day_of_week, e.recipe_id)
       p.week_start + e.day_of_week,
       e.recipe_id,
       e.servings
FROM meal_plan_entries e
JOIN meal_plans p ON p.id = e.plan_id
ORDER BY p.week_start + e.day_of_week,
         e.recipe_id,
         CASE e.meal_slot WHEN 'DINNER' THEN 0 WHEN 'LUNCH' THEN 1 ELSE 2 END;

-- Shopping lists hang off the Monday of their week rather than a plan row.
ALTER TABLE shopping_list_items ADD COLUMN week_start DATE;

UPDATE shopping_list_items s
SET week_start = p.week_start
FROM meal_plans p
WHERE p.id = s.plan_id;

DELETE FROM shopping_list_items WHERE week_start IS NULL;

ALTER TABLE shopping_list_items
    ALTER COLUMN week_start SET NOT NULL,
    DROP COLUMN plan_id;

CREATE INDEX shopping_list_items_week_idx ON shopping_list_items (week_start);

DROP TABLE meal_plan_entries;
DROP TABLE meal_plans;
