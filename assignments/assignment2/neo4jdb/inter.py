from neo4j.v1 import GraphDatabase
import sys

#Python puts quotes around strings, neo4j wont accept strings around dictionary keys.
class QueObj(dict):
    @staticmethod
    def isdict(d):
        t = type(d)
        if issubclass(t,dict) and t is not QueObj:
            return QueObj(d)
        return d

    def __init__(self,*arg,**kwarg):
        f = lambda x : {k : QueObj.isdict(x[k]) for k in x}
        arg = [f(l) for l in arg]
        kwarg = f(kwarg)
        super(QueObj,self).__init__(*arg,**kwarg)

    def __repr__(self):
        return "{{{}}}".format (
            ", " .join( "{} : {!r}".format(k,self[k]) for k in self)
        )

class Neo4jDB:
    def __init__ (self, uri = "bolt://localhost:7687", user = "neo4j", password = "password"):
        self.driver = GraphDatabase.driver(uri,auth=(user,password))

    def __exit__ (self,exc_type, exc_value, traceback):
        self.driver.close()

    # To prevent faulty queries.
    def transaction(self,query,**dict):
        a = QueObj(dict)
        #print(query.format(**a))
        with self.driver.session() as session :
            with session.begin_transaction() as tx:
                return tx.run(query.format(**a))


    #Create a node if it does not exists.
    def merge(self,*label,**dict):
        l = "".join(":{}".format(l) for l in label)
        self.transaction("Merge ({l} {d})",l=l,d = dict)
        return dict

    #Create a wrapper function around merge for a gin.
    def new_gin(self,name,producer,volume,percentage):
        return self.merge("Gin",
            name = name.upper(),
            producer = producer.upper(),
            volume = volume,
            percentage = percentage
        )
    #Create a wrapper function around merge for a tonic.
    def new_tonic(self,name,producer,volume):
        return self.merge("Tonic",
            name = name.upper(),
            producer = producer.upper(),
            volume = volume
        )
    #Create a wrapper function around merge for a garnish.
    def new_garnish(self,type):
        return self.merge("Garnish",
            type = type.upper()
        )

    #Create a user if it doesn't exists.
    def new_user(self, name, age, gender):
        return self.merge("User",
            name = name,
            age = age,
            gender = gender
        )


    # Searches the graph for a drink, or creates it if it doesn't exist. It then rates the drink.
    def drink(self, user, gin, tonic, garnish="", rating = None, comment = None):

        dict = {}
        if rating and 0 <= rating <= 5 :
            dict['rating'] = rating
        if comment :
            dict['comment'] = comment

        if garnish:
            garnish = "Match (a:Garnish{}) Merge (a)  <- [:Drink] -(d)".format(QueObj(garnish))

        self.transaction( """
            Match (g:Gin{gin})
            Match (t:Tonic{tonic})
            Match (u:User {user})
            {garnish}
            Merge (g) <- [:Drink] -(d)
            Merge (t)  <- [:Drink] -(d)
            Merge (u) - [:Rating] -> (r:Rating) - [:Rating] -> (d)
            Set r += {dict}
        """,
            garnish = garnish,
            gin = gin,
            tonic = tonic,
            user = user,
            dict = dict
        )
        return dict

    #Searches for a node with the given labels, and where the nodex matches the regular expression given from dictionary such that for (K:V) n.k = regex(V)
    def search(self,*labels,**regexdict):
        where = " and ".join("a.{} =~ {!r}".format(k,str(v)) for k,v in regexdict.items())
        if where:
            where = "Where " + where
        label = "".join(":{}".format(l) for l in labels)
        return self.transaction("Match (a{l}) {w} return a",l = label, w = where)

    #Searches for all relations given a gin, tonic and possibly a garnish. If sort is set to true, the list will be sorted by number of helpfull marks.
    def search_drink(self, gin, tonic, garnish = None, order=""):
        if garnish:
            garnish = "Match (garnish:Garnish{}) <- [:Drink] - (drink)".format(QueObj(garnish))
        else:
            garnish = "Where not (:Garnish) <- [:Drink] - (drink)"

        if order:
            order = "Order by {} DESC".format(order)

        r = self.transaction("""
            Match (gin:Gin{gin}) <- [:Drink] - (drink)
            Match (tonic:Tonic{tonic}) <- [:Drink] - (drink)
            Match (rating:Rating) - [:Rating] -> (drink)
            {garnish}
            Optional Match(rating:Rating) <- [helpfull:Helpfull] -()
            return rating, count(helpfull)
            {order}
        """,gin=gin, tonic=tonic, garnish=garnish, order = order)

        avg = self.transaction("""
            Match (gin:Gin{gin}) <- [:Drink] - (drink)
            Match (tonic:Tonic{tonic}) <- [:Drink] - (drink)
            Match (rating:Rating) - [:Rating] -> (drink)
            {garnish}
            return count(rating), avg(rating.rating)
        """,gin=gin, tonic=tonic, garnish=garnish)
        return (avg,r)

    #Marks a rating helpfull.
    def helpfull(self,user,rating):
        return self.transaction("""
            Match (rating:Rating{rating}), (user:User{user})
            Merge (user) - [h:Helpfull] -> (rating)
        """,user=user,rating=rating)

    #Returns all ratings from a user.
    def my_ratings(self,user,order = "count(helpfull)"):
        if order:
            order = "Order by {} DESC".format(order)
        return self.transaction("""
            Match (user:User{user}) - [:Rating] -> (rating:Rating) - [:Rating] -> (drink)
            Optional Match (rating:Rating) <- [helpfull:Helpfull] - ()
            return rating, count(helpfull)
            {order}
        """,user=user, order = order)

    #Returns all ratings, with the order being, per default, by the number of helpfull marks.
    def ratings(self, order = "count(helpfull)"):
        if order:
            order = "Order by {} DESC".format(order)

        return self.transaction("""
            Match (user:User) - [:Rating] -> (rating:Rating)
            Optional Match (:User) - [helpfull:Helpfull] -> (rating:Rating)
            return user,rating,count(helpfull)
            {order}
        """, order = order)

    #Clear the database.
    def clear(self):
        return self.transaction("""
            Match (u) detach delete u
        """)
