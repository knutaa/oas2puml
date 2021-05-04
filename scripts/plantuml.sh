#!/bin/bash

DIR=$(dirname "$0")

type='png'

while getopts t: flag
do
    case "${flag}" in
        t) type=${OPTARG};;
    esac
done
shift $((OPTIND-1))

for file in "$@" 
do
    echo "... processing $file"
    java -Djava.awt.headless=true -jar $DIR/plantuml.jar $file -t$type
done



