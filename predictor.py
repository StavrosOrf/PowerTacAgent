from keras.layers import Dense, Dropout
from keras.models import Sequential, load_model
from sklearn.preprocessing import MinMaxScaler
import pandas as pd
import numpy as np
import json
import socket
import os
import shutil


def train():

    # read weather bootstrap data file and make a data frame of it
    num_features = 7
    file = open('tempFiles/weather.boot.json')
    data = json.load(file)
    data = pd.DataFrame(data)
    # Split dataset to features and target
    dataset_arr = data.values
    x = dataset_arr[:, :num_features]
    y = dataset_arr[:, num_features:]
    file.close()

    scl = MinMaxScaler()
    x = scl.fit_transform(x)
    x = np.array(x)
    y = scl.fit_transform(y)
    y = np.array(y)

    num_neurons_in = 4
    num_neurons_hl1 = 7
    num_neurons_hl2 = 7
    num_neurons_out = 1

    dropout_rate = 0.01
    # TODO check in sim, laptop
    batch_size = 1
    epochs = 10

    # Build the model
    model = Sequential()

    model.add(Dense(units=num_neurons_in, activation='relu', input_shape=x[0].shape))
    model.add(Dropout(rate=dropout_rate))

    model.add(Dense(units=num_neurons_hl1, activation='relu'))
    model.add(Dropout(rate=dropout_rate))

    model.add(Dense(units=num_neurons_hl2, activation='relu'))
    model.add(Dropout(rate=dropout_rate))

    model.add(Dense(units=num_neurons_out))

    model.compile(optimizer='adam', loss='mean_squared_error')

    #model.summary()

    # Fit data to model
    model.fit(x, y, epochs=epochs, shuffle=False, batch_size=batch_size, verbose=0)

    # Save model to file
    model.save('./models/netUsage.h5')


def predict(forecast, model):
    # Read the weather data
    x = pd.DataFrame(forecast)
    x = np.array(x)
    # Scale the data frame
    scl = MinMaxScaler()
    x = scl.fit_transform(x)
    # Make net usage prediction for the next 24 hours
    model = model
    y = model.predict(x)
    #model.fit(x, y, shuffle=False, batch_size=1, verbose=0)
    #model.save('./models/netUsage.h5')
    y = [element * 100 for element in y]
    y = np.array(y)
    return y


def fit(model):
    file = open('./tempFiles/fit.online.json')
    data = json.load(file)
    df = pd.DataFrame(data, index=[0])
    dataset_arr = df.values
    num_features = 7
    # Read the correct weather data
    x = dataset_arr[:, :num_features]
    y = dataset_arr[:, num_features:]
    x = np.array(x)
    y = np.array(y)
    # Scale both data frames
    scl = MinMaxScaler()
    x = scl.fit_transform(x)
    y = scl.fit_transform(y)
    # Fit the data to the model
    model = model
    model.fit(x, y, shuffle=False, batch_size=1, verbose=0)
    model.save('./models/netUsage.h5')
    return


# train()
# for i in range(10):
#     file = open('./tempFiles/forecast24.online.json')
#     forecast = json.load(file)
#     file.close()
#     model = load_model('./models/netUsage.h5')
#     net_usage = predict(forecast, model)
#     print(net_usage)
#     fit(model)



PORT = 8098
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

print(sock)

server_address = ('localhost', PORT)
sock.bind(server_address)
sock.listen(1)
c = 0

# TODO disable flags
flag = False
flag2 = False

while True:

    print('\nwaiting for a connection')

    # wait for client input
    connection, client_address = sock.accept()
    data = connection.recv(16)
    data = data.decode('ascii')
    data = data.rstrip()

    if data == "boot_train":
        print("boot_train")
        flag = True
        y = "ok\n"
        connection.sendall(y.encode('utf-8'))
        train()
    elif data == "predict":
        if flag == False:
            y = "error\n"
            connection.sendall(y.encode('utf-8'))
        else:
            file = open('./tempFiles/forecast24.online.json')
            forecast = json.load(file)
            file.close()
            model = load_model('./models/netUsage.h5')
            net_usage = predict(forecast, model)
            flag2 = True
            y = ' '.join([str(elem) for elem in np.array(net_usage).flatten()])
            print("Predictions:")
            print(y)
            y = y + "\nok\n"
            connection.sendall(y.encode('utf-8'))
    elif data == "fit":
        print("fit")
        if flag2 == False:
            y = "error\n"
            connection.sendall(y.encode('utf-8'))
        else:
            model = load_model('./models/netUsage.h5')
            fit(model)
            y = "ok\n"
            connection.sendall(y.encode('utf-8'))
    elif data == "reset":
        print("reset..")
        flag = False
        if os.path.exists("models"):
            shutil.rmtree('models')

        os.mkdir("models")
        y = "ok\n"
        connection.sendall(y.encode('utf-8'))
    else:
        print("error")
    c = c + 1
