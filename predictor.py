from keras.layers import Dense, Dropout
from keras.models import Sequential, load_model
from sklearn.preprocessing import MinMaxScaler
import pandas as pd
import numpy as np
import json
import socket
import os
import shutil


def train(x, y):

    # scale features values
    scl = MinMaxScaler()
    x = scl.fit_transform(x)
    x = np.array(x).astype('float32')
    y = np.array(y).astype('float32')
    num_neurons_in = 7
    num_neurons_hl1 = 7
    num_neurons_hl2 = 7
    num_neurons_out = 1

    dropout_rate = 0.01
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

    model.add(Dense(units=num_neurons_out, activation='sigmoid'))

    model.compile(optimizer='adam', loss='mean_squared_error')

    #model.summary()
    # Fit data to model
    model.fit(x, y, epochs=epochs, shuffle=False, batch_size=batch_size, verbose=2)

    # Save model to file
    model.save('./models/peaks.h5')


def predict(forecast, model):
    # Read the weather data
    x = pd.DataFrame(forecast)
    # Scale the data frame
    scl = MinMaxScaler()
    x = scl.fit_transform(x)
    x = np.array(x).astype('float32')
    # Make net usage prediction for the next 24 hours
    model = model
    y = model.predict(x)
    y = [1.0 if y[i] > 0.5 else 0.0 for i in range(len(y))]
    y = np.array(y).astype('float32')
    return y


def fit(x, y, model):
    # Scale both data frames
    scl = MinMaxScaler()
    x = scl.fit_transform(x)
    x = np.array(x).astype('float32')
    y = np.array(y).astype('float32')
    # Fit the data to the model
    model = model
    model.fit(x, y, shuffle=False, batch_size=1, verbose=2)
    model.save('./models/peaks.h5')
    return


PORT = 8098
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

print(sock)

server_address = ('localhost', PORT)
sock.bind(server_address)
sock.listen(1)
c = 0

# TODO disable flags
trained = False
flag2 = False

while True:

    print('\nwaiting for a connection\n')

    # wait for client input
    connection, client_address = sock.accept()
    data = connection.recv(16)
    data = data.decode('ascii')
    print(data)
    whole_data = data.rstrip()
    data = data.rstrip().split()[0]

    if data == "boot_train":
        # TRAINING PHASE
        num_features = 7
        file = open('tempFiles/peaks.boot.json')
        dataset = json.load(file)
        dataset = pd.DataFrame(dataset)
        dataset['peak'] = np.where(dataset['peak'] == 'True', 1.0, 0.0)
        # Split dataset to features and target
        dataset_arr = dataset.values
        x = dataset_arr[:, :num_features]
        y = dataset_arr[:, num_features + 1:]
        file.close()
        
        trained = True
        out = "ok\n"
        connection.sendall(out.encode('utf-8'))
        train(x, y)
    elif data == "predict":
        if not trained:
            out = "error\n"
            connection.sendall(y.encode('utf-8'))
        else:
            file = open('./tempFiles/forecast24.online.json')
            forecast = json.load(file)
            print('Timeslot: ' + str(forecast[0]['timeslot']))
            file.close()
            model = load_model('./models/peaks.h5')
            peaks = predict(forecast, model)

            out = ' '.join([str(elem) for elem in np.array(peaks).flatten()])
            print("Predictions:")
            print(out)
            out = out + "\nok\n"
            connection.sendall(out.encode('utf-8'))

    elif data == "fit":
        model = load_model('./models/peaks.h5')
        file = open('./tempFiles/peak.online.json')
        fit_data = json.load(file)
        file.close()
        df = pd.DataFrame(fit_data, index=[0])
        correct = df.values
        dataset = dataset.append(df, ignore_index=True)
        num_features = 7
        # Read the correct weather data
        x = correct[:, :num_features]
        y = correct[:, num_features + 1:]
        fit(x, y, model)
        out = "ok\n"
        connection.sendall(out.encode('utf-8'))
    elif data == "retrain":
        print("retrain")
        print(whole_data.split()[1])
        threshold = int(whole_data.split()[1])
        print(type(threshold))
        # peaks values correction
        print(data)
        dataset['peak'] = np.where(dataset['netUsageMWh'] > threshold, 1.0, 0.0)
        dataset_arr = dataset.values
        x = dataset_arr[:, :num_features]
        y = dataset_arr[:, num_features + 1:]
        
        out = "ok\n"
        connection.sendall(out.encode('utf-8'))
        train(x, y)

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
