MOV AH, 3Ch
MOV DX, "prueba.txt"
INT 21H
MOV AH, 41h
MOV DX, "prueba.txt"
INT 21H
INT 20H