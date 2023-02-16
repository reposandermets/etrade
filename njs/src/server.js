const fastify = require('fastify');

const server = fastify();

server.get('/health', async (request, reply) => 'OK');
server.get('/', async (request, reply) => 'home');

server.post(
    '/signal',
    {},
    async (request, reply) => {
        reply.code(200).send({ status: 'Res from njs'});
        setTimeout(() => {
            console.log(`Message ${JSON.stringify(request.body)}`);
        }, 0);
    });

function bootServer() {
    server.listen(3000, '0.0.0.0', (err, address) => {
        if (err) {
            console.error(err.message);
            process.exit(1);
        }
        console.log(`Server listening at ${address}`);
    });
}

module.exports.bootServer = bootServer;
