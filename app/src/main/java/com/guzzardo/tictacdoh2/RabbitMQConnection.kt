package com.guzzardo.tictacdoh2

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection

class RabbitMQConnection(var connection: Connection?, var channel: Channel?) {

}