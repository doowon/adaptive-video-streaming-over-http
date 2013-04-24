import socket
import time
import random
import thread

ipAddr = '128.110.79.225'
m3u8FileName = '/var/www/mystream.m3u8'
targetDuration = 10
streamAddr = 'http://' + ipAddr + '/'
videoFileName = 'mystream'
videoExtension = 'ts'
quality = 'high'
numOfChunks = 12

def serverForUsersInfo(ipAddr, port):
	s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	s.bind((ipAddr,port))
	s.listen(5)
	while True:
		client, address = s.accept()
		data = client.recv(1024)
		global quality
		if data is 'low':
			quality = 'low'
		elif data is 'medium':
			quality = 'medium'
		elif data is "high":
			quality = 'high'

def m3u8Handler():
	for i in range(4, numOfChunks + 2):
		time.sleep(targetDuration - 0.5)
		f = open(m3u8FileName, 'a')
		if i is (numOfChunks + 1):
			line = '#EXT-X-ENDLIST\n'
		else:
			"""if quality is 'low':
				qualityFolder = '2/'
			elif quality is 'medium':
				qualityFolder = '3/'
			else:
				qualityFolder = ''"""
			num = random.randint(1,3)
			if num is 2:
				qualityFolder = '2/'
			elif num is 1:
				qualityFolder = '3/'
			else:
				qualityFolder = ''
			line = '#EXTINF:' + str(targetDuration) + ',\n'
			line += streamAddr + qualityFolder + videoFileName + str(i) + '.' +videoExtension + '\n'
			print 'Appened a new line'
		f.write(line)
		f.close()

if __name__ == "__main__":
	print "Server is running....."
	try:
		thread.start_new_thread(serverForUsersInfo,(ipAddr, 6000,))
		m3u8Handler()
	except:
		print "Error: unable to start thread"
	while 1:
   		pass	