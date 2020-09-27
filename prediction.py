import sys
from keras.models import load_model
import pandas as pd
import json
import numpy as np
#from sklearn.preprocessing import MinMaxScaler
import socket

# import os
# os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
pd.options.mode.chained_assignment = None

def processDataTest(x, hours_look_back, jump=1):
    X = []
    for i in range(0, len(x) - hours_look_back + 1, jump):
        X.append(x[i:(i + hours_look_back)])
    return np.array(X)


def predictor(model,day,hour):
	
	hours_look_back = 24
	
	file = open('temp.json')
	data = json.load(file)
	#print (data["predictions"])
	df = pd.DataFrame(data["predictions"])
	print('timeslot: ' + str(data["timeslotIndex" ]))

	df = df.rename(columns={'id': 'Day'})
	df = df.rename(columns={'forecastTime': 'Hour'})
	
	for i in range(24):
		hour = hour + 1
		if hour > 23 :
			hour = 0
			day = day + 1
			if day > 7 :
				day = 0
				
		df.Hour[i] = hour
		df.Day[i] = day

	df = df.drop('Day', 1)

	
	#scl = MinMaxScaler()
	X = df.values
	#print(X)
	#X = scl.fit_transform(X)
	X = np.array(X)
	
	X_new = processDataTest(X, hours_look_back)
	print(df)
	#print(X)

	Y = model.predict(X_new)

	Y_new = [element * 100 for element in Y]
	Y_new = np.array(Y_new)
	# Y_new = np.array(scl.inverse_transform(Y.reshape(-1, 1)))

	Y_new = str(Y_new)

	#sys.stdout.write(Y_new)
	file.close()
	return Y_new


PORT = 8098   
model = load_model('LSTM_EP500_BS16.h5')	
		
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)	

print()
print(sock)			

server_address = ('localhost', PORT)
sock.bind(server_address)
sock.listen(1)
c = 0
while True:
	# Wait for a connection
	print( 'waiting for a connection')
#	if c == 50 :
#		print("reloading model..===================\n\n")
#		model = load_model('LSTM_EP500_BS16.h5')
#		c == 0

	#wait for client input
	connection, client_address = sock.accept()
	data = connection.recv(16)
	#data output is like => 'day hour\r\n' where day(1-7) and hour(0-23)
	data = data.decode('ascii')
	day = data.split(sep=" ",maxsplit=2)[0]
	hour = data.split(sep=" ",maxsplit=2)[1]
	print(hour)
	print(day)
	y = []
	y = predictor(model,int(day) ,int(hour))
	#print(y)
	y = ''.join([str(elem) for elem in y])
	y = y + '\n-\n' 
	print(y)
	#send output array as a string to client
	connection.sendall(y.encode('utf-8'))
	c = c + 1
	#exit()
	
	#connection.sendall(b'y')
	
	
	
