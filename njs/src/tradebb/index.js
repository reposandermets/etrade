const reqs = require('./reqs.js');

const exampleBuy = {
    "sig": 1,
    "ticker": "BTCUSDT",
    "atrtp": "AtrTp",
    "atrsl": "AtrSl",
    "risk": 1,
    "exchange": "BB"
}

const exampleBuyExit = {
    "sig": -2,
    "ticker": "BTCUSDT",
    "atrtp": "AtrTp",
    "atrsl": "AtrSl",
    "risk": 1,
    "exchange": "BB"
}

const exampleSell = {
    "sig": -1,
    "ticker": "BTCUSDT",
    "atrtp": "AtrTp",
    "atrsl": "AtrSl",
    "risk": 1,
    "exchange": "BB"
}

async function sell(sig) {
    const res = await reqs.submitOrder({
        side: "Sell",
        symbol: "BTCUSDT",
        qty: "0.001",
        orderType: "Market",
        timeInForce: "ImmediateOrCancel",
        stopLoss: "21100",
        slTriggerBy: "LastPrice",
    });
    console.log(res, res)
    return res;
}

async function exitPosition(sig, position) {
    if (position.side === 'Buy') {
        const res = await reqs.submitOrder({
            side: "Sell",
            symbol: "BTCUSDT",
            qty: position.size,
            orderType: "Market",
            timeInForce: "PostOnly",
            closeOnTrigger: true,
        });

        return res;
    }

    if (position.side === 'Sell') {
        const res = await reqs.submitOrder({
            side: "Buy",
            symbol: "BTCUSDT",
            qty: position.size,
            orderType: "Market",
            timeInForce: "PostOnly",
            closeOnTrigger: true,
        });

        return res;
    }
}

async function buy(sig) {
    const res = await reqs.submitOrder({
        side: "Buy",
        symbol: "BTCUSDT",
        qty: "0.001",
        orderType: "Market",
        timeInForce: "ImmediateOrCancel",
        stopLoss: "19542.3",
        slTriggerBy: "LastPrice",
    });

    console.log(res, res)

    const res2 = await reqs.submitOrder({
        side: "Sell",
        symbol: "BTCUSDT",
        qty: "0.001",
        orderType: "Limit",
        timeInForce: "PostOnly",
        price: "22542.3",
        reduceOnly: true,
    });

    return res;
}

// in check positions move sl only if no tp and profit pnl is at least 0.25
async function signalHandler(sig) {
    console.log('signalHandler', sig);

    const position = await reqs.getPosition(sig.ticker, 'USDT');

    console.log('position', position)

    const side = position.side;

    if (sig.sig === 1 && side === 'None') {

        console.log('buy', sig.ticker);
        await buy(sig);

    } else if (sig.sig === -1 && side === 'None') {

        console.log('sell', sig.ticker);
        await sell(sig);

    } else if (sig.sig === -2 && side === 'Buy') {

        console.log('buy exit', sig.ticker);
        await exitPosition(sig, position);

    } else if (sig.sig === 2 && side === 'Sell') {

        console.log('sell exit', sig.ticker);
        await exitPosition(sig, position);

    } else if (sig.sig === 1 && side === 'Sell') {

        console.log('exit sell and buy', sig.ticker);

    } else if (sig.sig === -1 && side === 'Buy') {

        console.log('exit buy and sell', sig.ticker);

    }
}

// signalHandler(exampleBuyExit)
//     .catch((e) => {
//         console.log('e', e);
//     });
