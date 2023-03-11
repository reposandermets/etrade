const positions = require('./tradebb/positions.js')
const server = require('./server.js')
const trade = require('./tradebb/index')


// positions.engine();

// server.bootServer();

const atr = 200;

// trade.signalHandler({
//     "sig": -1,
//     "ticker": "BTCUSDT",
//     "atrtp": atr * 0.5,
//     "atrsl": atr * 1.5,
//     "risk": 1,
//     "exchange": "BB"
// })
//     .catch((e) => {
//         console.log('e', e);
//     });

// console.log(0.000 * Math.round(0.001234/10));

// function calculatePositionSize(
//     risk,
//     atrSl,
//     lastPrice,
//     equityUSD,
// ) {
//     const close = lastPrice;
//     const atrRiskPerc = (atrSl * 100) / close;
//     const riskRatio = risk / atrRiskPerc;
//     const equity = equityUSD / close;
//     const positionSize = ((equity * riskRatio) / 100);
//     return positionSize;
// }

// const capital = 100;
// const risk = 20;
// const atrSl = 400;
// const lastPrice = 10000;
// let r = calculatePositionSize(risk, atrSl, lastPrice, capital)
// console.log(r);


// function calcStepSize(qty, stepSize) {
//     return Math.floor(qty / stepSize) * stepSize;
// }

// console.log(calcStepSize(4, 2));
