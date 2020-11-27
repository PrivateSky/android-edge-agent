/**
Minimal static web server based on https://developer.mozilla.org/en-US/docs/Learn/Server-side/Node_server_without_framework
To be used only to testing purposes
*/
var http = require('http');
var fs = require('fs');
var path = require('path');

let PORT = 3000

console.log("Current folder : " + __dirname);

var versions_server = http.createServer(function (request, response) {
    console.log('request ', request.url);

    var filePath =  request.url;
    if (filePath == '/' || filePath == '') {
        filePath = '/index.html';
    }

    filePath = __dirname + filePath

    var extname = String(path.extname(filePath)).toLowerCase();
    var mimeTypes = {
        '.html': 'text/html',
        '.js': 'text/javascript',
        '.css': 'text/css',
        '.json': 'application/json',
        '.png': 'image/png',
        '.jpg': 'image/jpg',
        '.gif': 'image/gif',
        '.svg': 'image/svg+xml',
        '.wav': 'audio/wav',
        '.mp4': 'video/mp4',
        '.woff': 'application/font-woff',
        '.ttf': 'application/font-ttf',
        '.eot': 'application/vnd.ms-fontobject',
        '.otf': 'application/font-otf',
        '.wasm': 'application/wasm'
    };

    var contentType = mimeTypes[extname] || 'application/octet-stream';

    fs.readFile(filePath, function(error, content) {
        console.log('Serving ' + filePath);
        if (error) {
            if(error.code == 'ENOENT') {
                fs.readFile('./404.html', function(error, content) {
                    response.writeHead(404, { 'Content-Type': 'text/html' });
                    response.end(content, 'utf-8');
                });
            }
            else {
                response.writeHead(500);
                response.end('Sorry, check with the site admin for error: '+error.code+' ..\n');
            }
        }
        else {
            response.writeHead(200, { 'Content-Type': contentType });
            response.end(content, 'utf-8');
        }
    });

});

versions_server.listen(PORT);
console.log('Server running on port ' + PORT);