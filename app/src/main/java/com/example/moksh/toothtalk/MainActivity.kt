package com.example.moksh.toothtalk


import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {
    //Reference variables for UI widgets
    private lateinit var btnListen: Button
    private lateinit var btnListDevices: Button
    private lateinit var lvDeviceList : ListView
    private lateinit var tvMsg: TextView
    private lateinit var etWriteMsg: EditText
    private lateinit var btnSend: Button
    private lateinit var tvStatus: TextView

    //Reference variable for BluetoothAdapter object
    lateinit var myBluetoothAdapter: BluetoothAdapter

    //Array for paired BT Devices
     private var pairedDevicesArray= arrayOfNulls<BluetoothDevice>(50)


    //Constants for the Handler
    val STATE_LISTENING = 1
    val STATE_CONNECTING = 2
    val STATE_CONNECTED = 3
    val STATE_CONNECTION_FAILED = 4
    val STATE_MESSAGE_RECEIVED = 5

    //Constant for bluetooth enable request
    val REQUEST_ENABLE_BLUETOOTH = 1

    //Constants for App Name and UUID
    val APP_NAME = "ToothTalk"
    val MY_UUID = UUID.fromString("ac2ebe84-e062-11e8-9f32-f2801f1b9fd1")

    //Reference variable for SendReceive object
    lateinit var sendReceive : SendReceive





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUIViews()

        //Enable bluetooth if it is disabled
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!myBluetoothAdapter.isEnabled){
            val intentBTEnable = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intentBTEnable, REQUEST_ENABLE_BLUETOOTH)
        }

        implementListeners()

    }

    //Function for setting up UI widgets
    private fun setupUIViews() {
        btnListen = findViewById(R.id.btnListen)
        btnListDevices = findViewById(R.id.btnListDevices)
        lvDeviceList = findViewById(R.id.lvDeviceList)
        tvMsg = findViewById(R.id.tvMsg)
        etWriteMsg = findViewById(R.id.etWriteMsg)
        btnSend = findViewById(R.id.btnSend)
        tvStatus = findViewById(R.id.tvStatus)
    }

    //Function for implementing onClickListeners to different UI widgets
    private fun implementListeners(){
        //List Devices Button onClickListener
        btnListDevices.setOnClickListener {
            //On button click, we want to show list of all paired devices in the ListView

            //Define a Set for already paired BT devices
            var pairedDevicesSet: Set<BluetoothDevice> = myBluetoothAdapter
                    .bondedDevices

            //Declare arrays for paired device names and paired devices
             var pairedDeviceNamesArray = arrayOfNulls<String>(pairedDevicesSet.size)
              //pairedDevicesArray = Array<BluetoothDevice>(pairedDevicesSet.size, null)

            var index = 0

            //Get all the device names and devices in the respective arrays from the set
            if(pairedDevicesSet.size > 0){
                for(device in pairedDevicesSet){
                    pairedDeviceNamesArray[index] = device.name
                    pairedDevicesArray[index] = device
                    index++
                }
            }

            //Define the arrayadapter for ListView
            val arrayAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, pairedDeviceNamesArray)

            //Set the adapter to ListView
            lvDeviceList.adapter = arrayAdapter
        }

        //Listen Button onClickListener
        btnListen.setOnClickListener {
            val serverClass = ServerClass()
            serverClass.start()
        }

        //Action when Paired Devices in the ListView are clicked
        lvDeviceList.setOnItemClickListener { parent, view, position, id ->
            val clientClass = ClientClass(pairedDevicesArray[position]!!)
            clientClass.start()
            tvStatus.setText("Connecting")
        }

        //Send Button Action
        btnSend.setOnClickListener {
            var string = etWriteMsg.text.toString()
            sendReceive.write(string.toByteArray())

        }
    }

    /*Create Handler for the status. This handler will listen the message from another thread and
    change the text of the Status TextView*/
    var handler = Handler(Handler.Callback { msg ->
        when (msg.what) {
            STATE_LISTENING -> tvStatus.setText("Listening")
            STATE_CONNECTING -> tvStatus.setText("Connecting")
            STATE_CONNECTED -> tvStatus.setText("Connected")
            STATE_CONNECTION_FAILED -> tvStatus.setText("Connection Failed")
            STATE_MESSAGE_RECEIVED ->{var readBuff = msg.obj as ByteArray
            var tempMsg = String(readBuff,0,msg.arg1)
            tvMsg.setText(tempMsg)}
        }
        true
    })

    //Thread for the Server
    inner class ServerClass : Thread() {
        //Reference variable for BluetoothServerSocket object
        private lateinit var bluetoothServerSocket: BluetoothServerSocket
         init {
             try {
                 //Initialisation of BluetoothServerSocket object
                 bluetoothServerSocket = myBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                         APP_NAME, MY_UUID)
             }catch (e:IOException){
                 e.printStackTrace()
             }
         }

         override fun run() {
             var bluetoothSocket : BluetoothSocket? = null
             while (bluetoothSocket == null){
                 try{
                     val message = Message.obtain()
                     message.what = STATE_CONNECTING
                     handler.sendMessage(message)

                     //Listen for the connection
                     bluetoothSocket = bluetoothServerSocket.accept()
                 }catch (e:IOException){
                     e.printStackTrace()
                     val message = Message.obtain()
                     message.what = STATE_CONNECTION_FAILED
                     handler.sendMessage(message)
                     break
                 }
                 /*If the connection is established, we will get a socket which will be used to
                 send and receive the messages*/
                 if (bluetoothSocket != null){
                     val message = Message.obtain()
                     message.what = STATE_CONNECTED
                     handler.sendMessage(message)
                     //Do something for Send/Receive
                     sendReceive = SendReceive(bluetoothSocket)
                     sendReceive.start()
                     break
                 }
             }
         }
     }

    //Thread for the client
    inner class ClientClass(device: BluetoothDevice) : Thread(){
        private lateinit var bluetoothSocket: BluetoothSocket
        private var bluetoothDevice: BluetoothDevice = device
        init {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID)
            }catch (e:IOException){
                e.printStackTrace()
            }
        }

        override fun run() {
            try {
                bluetoothSocket.connect()
                val message = Message.obtain()
                message.what = STATE_CONNECTED
                handler.sendMessage(message)

                //Do something for Send/Receive
                sendReceive = SendReceive(bluetoothSocket)
                sendReceive.start()
            }catch (e:IOException){
                e.printStackTrace()
                val message = Message.obtain()
                message.what = STATE_CONNECTION_FAILED
                handler.sendMessage(message)
            }
        }
    }

    //Thread for sending and receiving messages
    inner class SendReceive(socket: BluetoothSocket) : Thread(){
        private lateinit var bluetoothSocket:BluetoothSocket
        private lateinit var inputStream: InputStream
        private lateinit var outputStream: OutputStream
        init {
            bluetoothSocket = socket
            var tempIn:InputStream? = null
            var tempOut: OutputStream? = null
            try {
                tempIn = bluetoothSocket.inputStream
                tempOut = bluetoothSocket.outputStream
            }catch (e:IOException){
                e.printStackTrace()
            }
            inputStream = tempIn!!
            outputStream = tempOut!!
        }

        override fun run() {
            var buffer = ByteArray(1024)
            var bytes:Int
            while (true){
                try {
                    bytes = inputStream.read(buffer)
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer)
                            .sendToTarget()
                }catch (e:IOException){
                    e.printStackTrace()
                }
            }
        }

         fun write(bytes:ByteArray){
            try {
                outputStream.write(bytes)
            }catch (e:IOException){
                e.printStackTrace()
            }

        }
    }
    }




