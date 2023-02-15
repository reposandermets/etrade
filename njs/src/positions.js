const { ContractClient } = require('bybit-api');

const key = process.env.API_KEY;
const secret = process.env.API_SECRET;

const client = new ContractClient({
  key,
  secret,
  strict_param_validation: true,
});

const sleep = (ms) => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

function getCurrentTime() {
  const d = new Date();
  return `${d.getHours()}:${d.getMinutes()}:${d.getSeconds()}`;
}

const pnl = (side, avgEntry, lastPrice) => {
  if (side === 'Buy') {
    return ((Math.floor(100 * (100 * (lastPrice - avgEntry) / avgEntry))) / 100);
  } else {
    return ((Math.floor(100 * (100 * (avgEntry - lastPrice) / avgEntry))) / 100);
  }
}

const getSymbolTicker = async (symbol) => {
  try {
    const { retMsg, result } = await client.getSymbolTicker('', symbol);
    if (retMsg !== 'OK') {
      throw new Error('getKline failed ' + retMsg);
    }

    const [tickerInfo] = result.list;
    return tickerInfo;
  } catch (error) {
    console.error('getSymbolTicker failed: ', error);
    throw error;
  }
};

const getInstrumentInfo = async ({ category, symbol }) => {
  try {
    const { retMsg, result } = await client.getInstrumentInfo({
      category,
      symbol,
    });
    if (retMsg !== 'OK') {
      throw new Error('getInstrumentInfo failed ' + retMsg);
    }
    const [instrumentInfo] = result.list;
    return instrumentInfo;
  } catch (error) {
    console.error('getInstrumentInfo failed: ', error);
    throw error;
  }
};

const setTPSL = async ({
  positionIdx,
  stopLoss,
  symbol,
  takeProfit,
}) => {
  // positionIdx: "0",
  // symbol: "SOLUSDT",
  // slTriggerBy: "LastPrice",
  // stopLoss: "21.97",
  // takeProfit: "22.56",
  // tpTriggerBy: "LastPrice",

  /** 0-one-way, 1-buy side, 2-sell side */
  try {
    const slTriggerBy = 'LastPrice';
    const tpTriggerBy = 'LastPrice';
    return await client.setTPSL(
      takeProfit
        ? {
          positionIdx,
          slTriggerBy,
          stopLoss,
          symbol,
          takeProfit,
          tpTriggerBy,
        }
        : {
          positionIdx,
          slTriggerBy,
          stopLoss,
          symbol,
        }
    );
  } catch (error) {
    console.error('setTPSL failed: ', error);
    throw error;
  }
};

const calcSlPercentage = (side, entryPriceString, percentage) => {
  let entryPrice = Number(entryPriceString)
  if (side === 'Buy') {
    return entryPrice - (entryPrice * percentage / 100);
  } else {
    return entryPrice + (entryPrice * percentage / 100);
  }
};

const setPricePrecisionByTickSize = (price, tickSize) => {
  const precision = tickSize.toString().split('.')[1].length - 1;
  return price.toFixed(precision);
}

const handlePosSl = async (pos, p, instrumentInfo) => {
  const tickSize = instrumentInfo.priceFilter.tickSize;
  const currentSl = pos.stopLoss;
  const newSlRaw = calcSlPercentage(pos.side, pos.entryPrice, p)
  const newSl = setPricePrecisionByTickSize(newSlRaw, tickSize);

  if (
    (pos.side === 'Buy' && Number(newSl) > Number(currentSl))
    ||
    (pos.side === 'Sell' && Number(newSl) < Number(currentSl))
  ) {
    console.log('currentSl !== newSl', currentSl, newSl);

    await setTPSL({
      positionIdx: pos.positionIdx,
      symbol: pos.symbol,
      stopLoss: newSl,
    });

    console.log('SL changed');
  }
};

const handlePosition = async (pos) => {
  try {
    if (pos.size > 0) {

      const [ticker, instrument] = await Promise.allSettled([
        getSymbolTicker(pos.symbol),
        getInstrumentInfo({ category: 'linear', symbol: pos.symbol }),
      ]);
      const tickerInfo = ticker.value;
      const instrumentInfo = instrument.value;

      // const tickerInfo = await getSymbolTicker(pos.symbol);
      // const instrumentInfo = await getInstrumentInfo({ category: 'linear', symbol: pos.symbol });

      const currPNL = pnl(pos.side, pos.entryPrice, tickerInfo.lastPrice);

      console.log(getCurrentTime(), pos.symbol, 'pnl:', currPNL, pos.side,
        pos.size, 'at', pos.entryPrice);

      let p;
      if (currPNL >= 0.4 && currPNL < 0.5) {
        p = -0.2;
      } else if (currPNL >= 0.5 && currPNL < 0.75) {
        p = -0.25;
      } else if (currPNL >= 0.75 && currPNL < 1.0) {
        p = -0.5;
      }

      if (p) {
        await handlePosSl(pos, p, instrumentInfo);
      }

      console.log(' ');
      console.log('###############################');
      console.log(' ');
    }
  } catch (error) {
    console.error('handlePosition failed: ', pos.symbol, error);
  }
}

const getPositions = async () => {
  try {
    const { retMsg, result } = await client.getPositions({
      settleCoin: 'USDT',
    });
    if (retMsg !== 'OK') {
      throw new Error('getPositions failed ' + retMsg);
    }
    const res = result.list;
    for (const pos of res) {
      await handlePosition(pos);
      await sleep(333);
    }
  } catch (e) {
    console.error('request failed: ', e);
    throw e;
  }
};

const flow = async () => {
  try {
    await getPositions();
  } catch (e) {
    console.error('request failed: ', e);
    throw e;
  }

}

function engine() {
  const interval = 33;
  setTimeout(() => {
    console.log(getCurrentTime());
    flow()
      .then(() => {
        engine();
      })
      .catch(e => {
        console.log('flow failed: ', e);
        engine();
      });
  }, interval);
}

module.exports.engine = engine;

// engine();

// Next up 
// 1. Test current logic
// 2. Run it inside a docker container
// 3. Deploy it to a server

function sendMessageToDiscordUsingFetch(message) {
  const url = 'https://discord.com/api/webhooks/...';
  const data = {
    content: message,
  };
  fetch(url, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

async function fetchOneMinuteCandleDataFromBinance(symbol) {
  const url = `https://api.binance.com/api/v3/klines?symbol=${symbol}&interval=1m&limit=1`;
  const response = await fetch(url);
  const data = await response.json();
  return data[0];
}

async function fetchOneMinuteCandleDataFromByBit(symbol) {
  const url = `https://api.bybit.com/v2/public/kline/list?symbol=${symbol}&interval=1&limit=1`;
  const response = await fetch(url);
  const data = await response.json();
  return data.result[0];
}
async function fetchOneMinuteCandleDataFromBitmex(symbol) {
  const url = `https://www.bitmex.com/api/v1/trade/bucketed?binSize=1m&partial=false&symbol=${symbol}&count=1&reverse=true`;
  const response = await fetch(url);
  const data = await response.json();
  return data[0];
}
