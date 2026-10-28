"use client";
import {useParams} from 'next/navigation';
import {Learning} from '@/features/trainings/components/Learning';
export default function Page(){const {id}=useParams<{id:string}>();return <Learning id={id}/>;}
