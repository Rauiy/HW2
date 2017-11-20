/**
 * Created by Steven on 2017-11-20.
 */
public class AuctionItem {
    private final int startingPrice;
    private final int cost;
    private final String name;
    private int currentPrice;
    private boolean sold;

    public AuctionItem(int startingPrice, int cost, String name) {
        this.startingPrice = startingPrice;
        this.cost = cost;
        this.name = name;
        this.currentPrice = startingPrice;
        this.sold = false;
    }

    public int getStartingPrice() {
        return startingPrice;
    }

    public int getCost() {
        return cost;
    }

    public int getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(int modifier) {
        if(modifier > 0)
            this.currentPrice -= modifier;

        if(this.currentPrice < 0)
            this.currentPrice = 0;
    }

    public void sellItem(){
        sold = true;
    }
}
