import React from "react";
import { Card, CardContent, Typography, Link } from "@mui/material";

export default function Item({ id, index, url, text, pagerank, score, cosineSimiliarty }) {
  return (
    <div>
      <Card>
        <CardContent>
          <Typography variant="h5" component="div">
            <Link href={`${url}`} target="_blank">
              {url}
            </Link>
          </Typography>
          <Typography variant="body2">{text}</Typography>
          <p>PageRank: {pagerank}, CosineSimiliarty: {cosineSimiliarty}, Score: {score}</p>
        </CardContent>
      </Card>
    </div>
  );
}
