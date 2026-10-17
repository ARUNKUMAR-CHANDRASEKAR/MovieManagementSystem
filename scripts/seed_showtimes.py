"""Schedules sample shows for the next two days and books a few seats, through the GraphQL API.

Usage (with all services running):  python3 scripts/seed_showtimes.py [gateway-url]
Movies are matched by title; titles not in the database are created first. Safe to run again:
a movie that already has upcoming shows in a country is left alone.
"""
import json
import random
import sys
import urllib.request
from datetime import datetime, timedelta, timezone

GATEWAY = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080") + "/graphql"
LOCAL = timezone(timedelta(hours=8))  # Singapore and Malaysia are both UTC+8
TIMES = ["10:30", "14:00", "17:30", "21:00"]
PRICES = {"SINGAPORE": (1350, 1550), "MALAYSIA": (1800, 2100)}  # (day, evening) in cents

SHOWING = {
    "SINGAPORE": ["Jailer", "Leo", "We Are All Strangers", "A Good Fortune", "Dune: Part Two", "Oppenheimer"],
    "MALAYSIA": ["Jailer", "Leo", "Master", "Wicked", "Sinners", "A Good Fortune"],
}

# Used to create a sample movie when its title is not in the database yet.
SAMPLE_MOVIES = {
    "Jailer": (2023, "Action", "A retired jailer goes after a gang that threatens his family."),
    "Leo": (2023, "Action", "A cafe owner's quiet life unravels when gangsters mistake him for someone else."),
    "We Are All Strangers": (2022, "Drama", "Neighbours in a Singapore housing estate find their lives intertwined."),
    "A Good Fortune": (2024, "Comedy", "A family's luck changes after a chance win."),
    "Dune: Part Two": (2024, "Sci-Fi", "Paul Atreides unites with the Fremen to seek revenge."),
    "Oppenheimer": (2023, "Drama", "The story of the physicist behind the atomic bomb."),
    "Master": (2021, "Action", "An alcoholic professor is sent to a juvenile school run by a gangster."),
    "Wicked": (2024, "Musical", "The untold story of the witches of Oz."),
    "Sinners": (2025, "Horror", "Twin brothers return home and face an evil waiting for them."),
}


def gql(query, **variables):
    request = urllib.request.Request(GATEWAY, json.dumps({"query": query, "variables": variables}).encode(),
                                     {"Content-Type": "application/json"})
    body = json.load(urllib.request.urlopen(request))
    if body.get("errors"):
        raise SystemExit(body["errors"][0]["message"])
    return body["data"]


def main():
    random.seed(7)  # same sample bookings every run
    movies = {m["title"]: m["id"] for m in gql("{ movies(size: 100) { items { id title } } }")["movies"]["items"]}
    now = datetime.now(LOCAL)
    for country, titles in SHOWING.items():
        theatres = gql("query($c: Country) { theatres(country: $c) { id location } }", c=country)["theatres"]
        for title in titles:
            if title not in movies:
                year, genre, synopsis = SAMPLE_MOVIES[title]
                movies[title] = gql("mutation($i: MovieInput!) { createMovie(input: $i) { id } }",
                                    i={"title": title, "releaseYear": year, "genre": genre,
                                       "synopsis": synopsis})["createMovie"]["id"]
                print(f"created movie {title}")
            movie_id = movies[title]
            if gql("query($m: ID!, $c: Country) { showtimes(movieId: $m, country: $c) { id } }",
                   m=movie_id, c=country)["showtimes"]:
                print(f"skip {title} in {country}: already scheduled")
                continue
            count = 0
            for day in range(2):
                for time in TIMES:
                    hour, minute = map(int, time.split(":"))
                    starts = (now + timedelta(days=day)).replace(hour=hour, minute=minute, second=0, microsecond=0)
                    if starts <= now:
                        continue
                    price = PRICES[country][1 if hour >= 17 else 0]
                    for theatre in theatres:
                        show = gql("""mutation($i: ShowtimeInput!) { createShowtime(input: $i) { id } }""",
                                   i={"movieId": movie_id, "theatreId": theatre["id"],
                                      "startsAt": starts.isoformat(), "priceCents": price})["createShowtime"]
                        book_sample_seats(show["id"])
                        count += 1
            print(f"{title} in {country}: {count} shows")


def book_sample_seats(show_id):
    """Sells a block of seats so the seat map shows some as sold."""
    row = random.choice("CDEF")
    first = random.randint(1, 8)
    seats = [f"{row}{n}" for n in range(first, first + random.randint(2, 4))]
    gql("mutation($s: ID!, $seats: [String!]!) { bookSeats(showtimeId: $s, seats: $seats, customerName: \"Sample\") { reference } }",
        s=show_id, seats=seats)


if __name__ == "__main__":
    main()
