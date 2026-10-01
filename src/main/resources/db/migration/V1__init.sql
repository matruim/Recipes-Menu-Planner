-- Core schema for the family meal planner.
-- Ingredients are canonicalised so that "2 tbsp olive oil" in one recipe and
-- "1/4 cup Olive Oil" in another roll up to a single shopping list line.

CREATE TABLE recipes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         TEXT        NOT NULL,
    description   TEXT        NOT NULL DEFAULT '',
    instructions  TEXT        NOT NULL DEFAULT '',
    servings      INTEGER     NOT NULL DEFAULT 4 CHECK (servings > 0),
    prep_minutes  INTEGER     NOT NULL DEFAULT 0 CHECK (prep_minutes >= 0),
    cook_minutes  INTEGER     NOT NULL DEFAULT 0 CHECK (cook_minutes >= 0),
    source_url    TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX recipes_title_lower_idx ON recipes (lower(title));

CREATE TABLE ingredients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    -- lowercased, whitespace-collapsed form used for de-duplication
    normalized_name TEXT NOT NULL UNIQUE,
    category        TEXT NOT NULL DEFAULT 'OTHER'
);

CREATE TABLE recipe_ingredients (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id     UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients (id) ON DELETE RESTRICT,
    -- NULL quantity means "to taste" / unmeasured
    quantity      NUMERIC(10, 3),
    unit          TEXT    NOT NULL DEFAULT 'COUNT',
    note          TEXT    NOT NULL DEFAULT '',
    position      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX recipe_ingredients_recipe_idx ON recipe_ingredients (recipe_id);

CREATE TABLE meal_plans (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Monday of the planned week; one plan per week
    week_start DATE      NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE meal_plan_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id     UUID    NOT NULL REFERENCES meal_plans (id) ON DELETE CASCADE,
    recipe_id   UUID    NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    -- 0 = Monday .. 6 = Sunday, matching java.time.DayOfWeek ordinal
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    meal_slot   TEXT    NOT NULL,
    servings    INTEGER NOT NULL CHECK (servings > 0),
    UNIQUE (plan_id, day_of_week, meal_slot, recipe_id)
);

CREATE INDEX meal_plan_entries_plan_idx ON meal_plan_entries (plan_id);

CREATE TABLE shopping_list_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id       UUID    NOT NULL REFERENCES meal_plans (id) ON DELETE CASCADE,
    -- set for generated lines; NULL for anything typed in by hand
    ingredient_id UUID REFERENCES ingredients (id) ON DELETE CASCADE,
    label         TEXT    NOT NULL,
    quantity      NUMERIC(10, 3),
    unit          TEXT    NOT NULL DEFAULT 'COUNT',
    category      TEXT    NOT NULL DEFAULT 'OTHER',
    checked       BOOLEAN NOT NULL DEFAULT FALSE,
    -- manual lines survive regeneration; generated ones are rebuilt each time
    manual        BOOLEAN NOT NULL DEFAULT FALSE,
    position      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX shopping_list_items_plan_idx ON shopping_list_items (plan_id);
