import jade.core.AID;

import java.io.Serializable;

/**
 * Created by Steven on 2017-11-20.
 */
public class AuctionItem implements Serializable{
    private final int startingPrice;
    private final int lowestPrice;
    private final int cost;
    private final String name;
    private int currentPrice;
    private boolean sold;
    private boolean limit;
    private AID buyer;

    public AuctionItem(int startingPrice, int cost, String name) {
        this.startingPrice = startingPrice;
        this.cost = cost;
        this.lowestPrice = (int)(cost*1.5);
        this.name = name;
        this.currentPrice = startingPrice;
        this.sold = false;
        buyer = null;
        limit = false;
    }

    public String getName() {
        return name;
    }

    public AID getBuyer(){
        return buyer;
    }

    public int getStartingPrice() {
        return startingPrice;
    }

    public boolean getLimit() {
        return limit;
    }

    public int getCurrentPrice() {
        return currentPrice;
    }

    public void setBuyer(AID b){
        buyer = b;
        sold = true;
    }

    public void decreasePrice(double modifier) {
        if(modifier > 0)
            this.currentPrice -= modifier;

        if(this.currentPrice < lowestPrice)
            limit = true;
    }

    public void decreasePercentage(double modifier){

        this.currentPrice = (int)(this.currentPrice*modifier);
        if(this.currentPrice < lowestPrice)
            limit = true;
    }

    public boolean isSold(){return sold;}

    @Override
    public String toString() {
        return "AuctionItem{" +
                "startingPrice=" + startingPrice +
                ", lowestPrice=" + lowestPrice +
                ", name='" + name + '\'' +
                '}';
    }
}
