def testlist(db,lf):
    for n,f in enumerate(lf):
        if n > 0 :
            input("\n\nPress enter for new test...")
        print("TEST ",n + 1)
        print(linelist(f(db)))

def linelist(l):
    return "\n".join(str(i.values()) for i in l)

def real_db(db):
    return dict(
        Jonas = db.new_user("Jonas Ingerslev Sørensen", 21 , "Male"),
        Jeff = db.new_user("Jeff Gyldenbrand", 33, "Apache Attack Helicopter"),
        Simon = db.new_user("Simon Dradrach Jørgensen", 26, "Emu"),

        gin = db.new_gin("Bombay Sapphire", "Bombay spirits", 70, 40),
        tonic = db.new_tonic("Tonic water", "Schweppes", 33),
        garnish = db.new_garnish("Lime")
    )

def db_output(db):
    db.clear()
    real_db(db)
    result = db.transaction("""
        match (u) return u
    """)
    return list(result)


def ratings(db):
    db.clear()
    def f(Jeff,Jonas,Simon,gin,tonic,garnish):
        db.drink(Jeff,gin,tonic, rating = 4, comment = "Needs more pickles.")
        db.drink(Jonas,gin,tonic, rating = 1, comment = "It's a shit!")
        db.drink(Simon,gin,tonic,garnish, 3, "Tastes like Australian tears")
        return list(db.transaction("""
            match (rating:Rating) - [:Rating] -> (drink)
            return rating, drink
        """))
    return f(**real_db(db))


def helpfull(db):
    db.clear()
    def f(Jeff,Jonas,Simon,gin,tonic,garnish):
        db.drink(Jeff,gin,tonic,rating=4, comment = "Needs more pickles.")
        db.drink(Jonas,gin,tonic,rating=1, comment = "It's a shit!")
        rating = db.drink(Simon,gin,tonic,garnish, 3, "Tastes like Australian tears")
        db.helpfull(Jeff,rating)
        return list(db.transaction("""
            match ()-[u:Helpfull]->() return u
        """))

    return f(**real_db(db))

def search(db):
    helpfull(db)
    l = [
        db.search("Gin"),
        db.search(percentage = 50),
        db.search(percentage = "^([5-9]|[0-9])"),
        db.search(producer = "(?i)Schweppes"),
        db.search("Garnish"),
        db.search(name = "(?i)Jonas*")
    ]
    return [y for x in l for y in x]

def search_drinks(db):
    helpfull(db)
    def f(gin,tonic,garnish,**_):
        l =  [
            db.search_drink(gin,tonic),
            db.search_drink(gin,tonic,order="count(helpfull)"),
            db.search_drink(gin,tonic,garnish),
            db.search_drink(gin,tonic,garnish,order="count(helpfull)")
        ]
        return [y for x in l for y in x]
    return f(**real_db(db))

def my_ratings(db):
    helpfull(db)
    def f(Jonas,Jeff,Simon,**_):
        l = [
            db.my_ratings(Jonas),
            db.my_ratings(Simon),
            db.my_ratings(Jeff)
        ]
        return [y for x in l for y in x]
    return f(**real_db(db))

def all_rating(db):
    helpfull(db)
    l=[
        db.ratings(),
        db.ratings("rating.rating"),
        db.ratings("user.name")
    ]
    return [y for x in l for y in x]
if __name__ == '__main__':
    from inter import *

    db = Neo4jDB()
    testlist(db,[db_output,ratings,helpfull,search,search_drinks,my_ratings,all_rating])
