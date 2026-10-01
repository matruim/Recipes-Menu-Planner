#!/usr/bin/env bash
# Adds a handful of demo recipes through the running app, so a fresh install has
# something to plan with. Safe to skip entirely; nothing else depends on it.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

if ! curl -fsS -o /dev/null "$BASE_URL/recipes"; then
  echo "Cannot reach $BASE_URL - start the app first (./gradlew run)." >&2
  exit 1
fi

add_recipe() {
  local title="$1" description="$2" servings="$3" prep="$4" cook="$5" ingredients="$6" instructions="$7"
  curl -fsS -o /dev/null -X POST "$BASE_URL/recipes" \
    --data-urlencode "title=$title" \
    --data-urlencode "description=$description" \
    --data-urlencode "servings=$servings" \
    --data-urlencode "prepMinutes=$prep" \
    --data-urlencode "cookMinutes=$cook" \
    --data-urlencode "ingredients=$ingredients" \
    --data-urlencode "instructions=$instructions"
  echo "  added: $title"
}

echo "Seeding demo recipes into $BASE_URL"

add_recipe "Garlic Butter Pasta" "Weeknight pasta that leans on the pantry" 4 10 20 \
'1 lb spaghetti
4 tbsp butter
6 cloves garlic, minced
1/2 cup parmesan cheese, grated
2 tbsp olive oil
salt, to taste' \
'Boil the spaghetti in well salted water.
Melt the butter with the garlic over low heat.
Toss the drained pasta through the butter with the parmesan.'

add_recipe "Sheet Pan Chicken" "Everything roasts on one tray" 4 15 40 \
'2 lbs chicken thighs
1/4 cup olive oil
3 cloves garlic, minced
1 lb potatoes, quartered
1 tsp paprika
salt, to taste' \
'Heat the oven to 425F.
Toss everything together on a sheet pan.
Roast for 40 minutes, turning once.'

add_recipe "Tomato Soup" "Deliberately written in metric" 6 10 30 \
'2 tbsp olive oil
1 onion, diced
4 cloves garlic
800 g canned tomatoes
500 ml vegetable broth
1/2 cup cream' \
'Sweat the onion and garlic in the oil.
Add the tomatoes and broth, then simmer for 30 minutes.
Blend smooth and stir in the cream.'

add_recipe "Black Bean Tacos" "Fast meatless dinner" 4 10 15 \
'2 cans black beans, drained
1 tbsp olive oil
1 onion, diced
2 tsp cumin
1 tsp chili powder
8 tortillas
1 avocado, sliced
1/2 cup sour cream' \
'Cook the onion in the oil until soft.
Add the beans and spices, mashing lightly.
Warm the tortillas and fill with beans, avocado and sour cream.'

add_recipe "Overnight Oats" "Breakfast made the night before" 2 5 0 \
'1 cup oats
1 cup milk
2 tbsp honey
1/2 cup berries
1 pinch salt' \
'Stir everything together in a jar.
Refrigerate overnight.
Top with extra berries before eating.'

add_recipe "Greek Salad" "No cooking required" 4 15 0 \
'4 tomatoes, wedged
1 cucumber, sliced
1/2 red onion, thinly sliced
200 g feta cheese
1/4 cup olive oil
2 tbsp vinegar
1 tsp oregano
salt, to taste' \
'Combine the vegetables in a bowl.
Whisk the oil, vinegar and oregano together.
Pour over the salad and crumble the feta on top.'

echo "Done. Open $BASE_URL/recipes"
