package housing;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.math3.distribution.LogNormalDistribution;

/**********************************************************
 * Implementation of the mechanism of the house-sale and
 * house-rental markets.
 * 
 * See model description for details.
 * 
 * @author daniel
 *
 *********************************************************/
public class HousingMarket {
	
	/////////////////////////////////////////////////////////////////////////////////
	// Configuration
	/////////////////////////////////////////////////////////////////////////////////
	static public class Config {
		public static final double HPI_LOG_MEDIAN = Math.log(195000); // Median price from ONS: 2013 housse price index data tables table 34
		public static final double HPI_SHAPE = 0.555; // shape parameter for lognormal dist. ONS: 2013 house price index data tables table 34
		public static final double HPI_MEAN = Math.exp(HPI_LOG_MEDIAN + HPI_SHAPE*HPI_SHAPE/2.0);
		public static LogNormalDistribution listPriceDistribution = new LogNormalDistribution(HPI_LOG_MEDIAN, HPI_SHAPE);
		public static final double T = 200.0; // characteristic number of data-points over which to average market statistics
		public static final double F = Math.exp(-1.0/12.0); // House Price Index appreciation decay const (in market clearings)
		public static final double E = Math.exp(-1.0/T); // decay const for averaging days on market
		public static final double G = Math.exp(-1.0/8); // Decay const for averageListPrice averaging
	}

	/////////////////////////////////////////////////////////////////////////////////
	// Diagnostics
	/////////////////////////////////////////////////////////////////////////////////
	public class Diagnostics {

		public double averageSoldPriceToOLP;
		public double averageBidPrice;
		public double averageOfferPrice;
		public int    nSales, saleCount;
		public int    nBuyers;
		public int    nSellers;
	    public double [][]    priceData;
	    public double [][]    referencePriceData;
		double [] offerPrices;
		double [] bidPrices;

		public Diagnostics() {
			averageSoldPriceToOLP = 1.0;
			saleCount = 0;
			nSales = 0;
			nBuyers = 0;
			nSellers = 0;
	        priceData = new double[2][House.Config.N_QUALITY];
	        referencePriceData = new double[2][House.Config.N_QUALITY];
	        int i;
	        for(i=0; i<House.Config.N_QUALITY; ++i) {
	        	priceData[0][i] = HousingMarket.referencePrice(i);
	        	referencePriceData[0][i] = HousingMarket.referencePrice(i);
	        	referencePriceData[1][i] = HousingMarket.referencePrice(i);
	        }

		}

		public void record() {
			nSales = saleCount;
			saleCount = 0;
			nSellers = onMarket.size();
			nBuyers = buyers.size();

			// -- Record average bid price
			// ---------------------------
			averageBidPrice = 0.0;
			for(HouseBuyerRecord buyer : buyers) {
				averageBidPrice += buyer.price;
			}
			if(buyers.size() > 0) averageBidPrice /= buyers.size();

			// -- Record average offer price
			// -----------------------------
			averageOfferPrice = 0.0;
			for(HouseSaleRecord sale : onMarket.values()) {
				averageOfferPrice += sale.currentPrice;
			}
			if(onMarket.size() > 0) averageOfferPrice /= onMarket.size();
			recordOfferPrices();
			recordBidPrices();
		}
		
		public void step() {
	        int i;
	        for(i=0; i<House.Config.N_QUALITY; ++i) {
	        	priceData[1][i] = HousingMarketTest.housingMarket.averageSalePrice[i];
	        }
		}
		
		public void recordSale(HouseSaleRecord sale) {
			if(sale.initialListedPrice > 0.01) {
				averageSoldPriceToOLP = Config.E*averageSoldPriceToOLP + (1.0-Config.E)*sale.currentPrice/sale.initialListedPrice;
			}
			saleCount += 1;			
		}
		
		public void recordBid(double price) {
//			bidCount += 1;
//			averageBidPrice += price;
		}
		
		protected void recordOfferPrices() {
			offerPrices = new double[onMarket.size()];
			int i = 0;
			for(HouseSaleRecord sale : onMarket.values()) {
				offerPrices[i] = sale.currentPrice;
				++i;
			}
		}

		protected void recordBidPrices() {
			bidPrices = new double[buyers.size()];
			int i = 0;
			
			for(HouseBuyerRecord bid : buyers) {
				bidPrices[i] = bid.price;
				++i;
			}
		}
		public double[] getOfferPrices() {
			return(offerPrices);
		}

		public double[] getBidPrices() {
			return(bidPrices);
		}

	}
	
	public HousingMarket() {
		int i;
		
		for(i = 0; i<House.Config.N_QUALITY; ++i) {
			averageSalePrice[i] = referencePrice(i);
		}
		housePriceIndex = 1.0;
		lastHousePriceIndex = 1.0;
		HPIAppreciation = 0.0;
		averageDaysOnMarket = 30;
		diagnostics = this.new Diagnostics();
	}
	
	/******************************************
	 * Put a new offer on the market.
	 * @param house House to put on the market
	 * @param price List price for the house.
	 ******************************************/
	public void offer(House house, double price) {
		onMarket.put(house, new HouseSaleRecord(house, price));
	}
	
	/******************************************
	 * Change the list-price on a house that is already on
	 * the market.
	 * 
	 * @param h The house to change the price for.
	 * @param newPrice The new price of the house.
	 ******************************************/
	public void updateOffer(House h, double newPrice) {
		onMarket.get(h).currentPrice = newPrice;
	}
	
	/*******************************************
	 * Take a house off the market.
	 * 
	 * @param house The house to take off the market.
	 *******************************************/
	public void removeOffer(House house) {
		onMarket.remove(house);
	}

	/*******************************************
	 * Make a bid on the market (i.e. make an offer on
	 * a (yet to be decided) house).
	 * 
	 * @param buyer The household that is making the bid.
	 * @param price The price that the household is willing to pay.
	 ******************************************/
	public void bid(Household buyer, double price) {
		buyers.add(new HouseBuyerRecord(buyer, price));
		diagnostics.recordBid(price);
	}

	/**************************************************
	 * Get information on a given house that is on the market.
	 * @param h House we're interested in.
	 * @return The sale-record for the given house (NULL if not on the market)
	 *************************************************/
	public HouseSaleRecord getSaleRecord(House h) {
		return(onMarket.get(h));
	}
	
	/**************************************************
	 * Clears all current bids and offers on the housing market.
	 * 
	 **************************************************/
	public void clearMarket() {
		HouseBuyerRecord buyer;
		HouseSaleRecord  seller;
		HouseSaleRecord	 ceilingSeller = new HouseSaleRecord(new House(), 0.0);

		recordMarketStats();

		// --- create set of sellers, sorted by quality then price
		// --- (TODO: better computational complexity with R-tree (or KD-tree))
		// ---
		TreeSet<HouseSaleRecord> sellers = new TreeSet<HouseSaleRecord>();
		for(HouseSaleRecord sale : onMarket.values()) {
			sellers.add(sale);
		}
		
		while(!buyers.isEmpty()) {
			buyer = buyers.poll();
			ceilingSeller.quality = House.Config.N_QUALITY;
			seller = sellers.lower(ceilingSeller); // cheapest seller at this quality
			while(seller != null && 
				(seller.currentPrice > buyer.price || seller.house.owner == buyer.buyer)) {
				ceilingSeller.quality = seller.quality-1;
				seller = sellers.lower(ceilingSeller); // cheapest seller at this quality
			}
			if(seller != null) {
				completeTransaction(buyer, seller);
				removeOffer(seller.house);
				sellers.remove(seller);
			}
		}
	}

	/**********************************************
	 * Do all stuff necessary when a buyer and seller is matched
	 * and the transaction is completed.
	 * 
	 * @param b The buyer's record
	 * @param sale The seller's record
	 **********************************************/
	public void completeTransaction(HouseBuyerRecord b, HouseSaleRecord sale) {
		// --- update sales statistics		
		averageDaysOnMarket = Config.E*averageDaysOnMarket + (1.0-Config.E)*30*(HousingMarketTest.t - sale.tInitialListing);
		averageSalePrice[sale.quality] = Config.G*averageSalePrice[sale.quality] + (1.0-Config.G)*sale.currentPrice;
		diagnostics.recordSale(sale);
	}
		
	public boolean isOnMarket(House h) {
		return(onMarket.containsKey(h));
	}
	
	/***************************************************
	 * Get the annualised appreciation in house price index (HPI is compared to the
	 * reference HPI_MEAN)
	 * 
	 * @return Annualised appreciation
	 ***************************************************/
	public double housePriceAppreciation() {
		return(12.0*HPIAppreciation);
	}
	
	/***********************************************
	 * HPI reference price of a house for a given quality
	 * 
	 * @param q quality of the house
	************************************************/
	static public double referencePrice(int q) {
		return(Config.listPriceDistribution.inverseCumulativeProbability((q+0.5)/House.Config.N_QUALITY));
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////
	
	protected void recordMarketStats() {
		// --- House Price Index stuff
		// ---------------------------
		HPIAppreciation = Config.F*HPIAppreciation - (1.0-Config.F)*housePriceIndex;
		housePriceIndex = 0.0;
		for(Double price : averageSalePrice) {
			housePriceIndex += price; // TODO: assumes equal distribution of houses over qualities
		}
		housePriceIndex /= House.Config.N_QUALITY*Config.HPI_MEAN;
		HPIAppreciation += (1.0-Config.F)*housePriceIndex;

		diagnostics.record();
	}

	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	
	public double getHPIAppreciation() {
		return HPIAppreciation;
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////

	protected Map<House, HouseSaleRecord> 	onMarket = new TreeMap<House, HouseSaleRecord>();
	protected PriorityQueue<HouseBuyerRecord> buyers = new PriorityQueue<HouseBuyerRecord>();
	
	// ---- statistics
	public double averageDaysOnMarket;
	public double averageSalePrice[] = new double[House.Config.N_QUALITY];
	public double HPIAppreciation;
	public double housePriceIndex;

	public double lastHousePriceIndex;
	
	public Diagnostics diagnostics;

	/**
	public static final double T = 200.0; // characteristic number of data-points over which to average market statistics
	public static final double E = Math.exp(-1.0/T); // decay const for averaging
	public static final double F = Math.exp(-1.0/12.0); // House Price Index appreciation decay const (in market clearings)
	public static final double G = Math.exp(-1.0/8); // Decay const for averageListPrice averaging
	public static final double HPI_LOG_MEDIAN = Math.log(195000); // Median price from ONS: 2013 housse price index data tables table 34
	public static final double HPI_SHAPE = 0.555; // shape parameter for lognormal dist. ONS: 2013 house price index data tables table 34
	public static final double HPI_MEAN = Math.exp(HPI_LOG_MEDIAN + HPI_SHAPE*HPI_SHAPE/2.0);
	public static LogNormalDistribution listPriceDistribution = new LogNormalDistribution(HPI_LOG_MEDIAN, HPI_SHAPE);
	**/
}
